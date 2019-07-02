/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.java.debug.core.protocol.Events.DebugEvent;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public abstract class AbstractProtocolServer implements IProtocolServer {
    private static final Logger logger = Logger.getLogger("java-debug");
    private static final int BUFFER_SIZE = 4096;
    private static final String TWO_CRLF = "\r\n\r\n";
    private static final Pattern CONTENT_LENGTH_MATCHER = Pattern.compile("Content-Length: (\\d+)");
    private static final Charset PROTOCOL_ENCODING = StandardCharsets.UTF_8; // vscode protocol uses UTF-8 as encoding format.

    protected boolean terminateSession = false;

    private Reader reader;
    private Writer writer;

    private ByteBuffer rawData;
    private int contentLength = -1;
    private AtomicInteger sequenceNumber = new AtomicInteger(1);

    private PublishSubject<Messages.Response> responseSubject = PublishSubject.<Messages.Response>create();
    private PublishSubject<Messages.Request> requestSubject = PublishSubject.<Messages.Request>create();

    /**
     * Constructs a protocol server instance based on the given input stream and
     * output stream.
     *
     * @param input
     *            the input stream
     * @param output
     *            the output stream
     */
    public AbstractProtocolServer(InputStream input, OutputStream output) {
        this.reader = new BufferedReader(new InputStreamReader(input, PROTOCOL_ENCODING));
        this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(output, PROTOCOL_ENCODING)));
        this.contentLength = -1;
        this.rawData = new ByteBuffer();

        requestSubject.observeOn(Schedulers.newThread()).subscribe(request -> {
            try {
                this.dispatchRequest(request);
            } catch (Exception e) {
                logger.log(Level.SEVERE, String.format("Dispatch debug protocol error: %s", e.toString()), e);
            }
        });
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    public void run() {
        char[] buffer = new char[BUFFER_SIZE];
        try {
            while (!this.terminateSession) {
                if (this.reader.ready()) {
                    int read = this.reader.read(buffer, 0, BUFFER_SIZE);
                    if (read == -1) {
                        break;
                    }

                    this.rawData.append(new String(buffer, 0, read).getBytes(PROTOCOL_ENCODING));
                    this.processData();
                } else {
                    Thread.sleep(250);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("Read data from io exception: %s", e.toString()), e);
        } catch (InterruptedException e) {
            stop();
        }

        requestSubject.onComplete();
    }

    /**
     * Sets terminateSession flag to true. And the dispatcher loop will be
     * terminated after current dispatching operation finishes.
     */
    public void stop() {
        this.terminateSession = true;
    }

    /**
     * Send a request/response/event to the DA.
     *
     * @param message
     *            the message.
     */
    private void sendMessage(Messages.ProtocolMessage message) {
        message.seq = this.sequenceNumber.getAndIncrement();

        String jsonMessage = JsonUtils.toJson(message);
        byte[] jsonBytes = jsonMessage.getBytes(PROTOCOL_ENCODING);

        String header = String.format("Content-Length: %d%s", jsonBytes.length, TWO_CRLF);
        byte[] headerBytes = header.getBytes(PROTOCOL_ENCODING);

        ByteBuffer data = new ByteBuffer();
        data.append(headerBytes);
        data.append(jsonBytes);

        String utf8Data = data.getString(PROTOCOL_ENCODING);

        try {
            if (message instanceof Messages.Request) {
                logger.fine("\n[[REQUEST]]\n" + utf8Data);
            } else if (message instanceof Messages.Event) {
                logger.fine("\n[[EVENT]]\n" + utf8Data);
            } else {
                logger.fine("\n[[RESPONSE]]\n" + utf8Data);
            }
            this.writer.write(utf8Data);
            this.writer.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("Write data to io exception: %s", e.toString()), e);
        }
    }

    @Override
    public void sendEvent(DebugEvent event) {
        sendMessage(new Messages.Event(event.type, event));
    }

    @Override
    public void sendResponse(Messages.Response response) {
        sendMessage(response);
    }

    @Override
    public CompletableFuture<Messages.Response> sendRequest(Messages.Request request) {
        return sendRequest(request, 0);
    }

    @Override
    public CompletableFuture<Messages.Response> sendRequest(Messages.Request request, long timeout) {
        CompletableFuture<Messages.Response> future = new CompletableFuture<>();
        Timer timer = new Timer();
        Disposable[] disposable = new Disposable[1];
        disposable[0] = responseSubject.filter(response -> response.request_seq == request.seq).take(1)
                .observeOn(Schedulers.newThread()).subscribe((response) -> {
                    try {
                        timer.cancel();
                        future.complete(response);
                        if (disposable[0] != null) {
                            disposable[0].dispose();
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, String.format("Handle response error: %s", e.toString()), e);
                    }
                });
        sendMessage(request);
        if (timeout > 0) {
            try {
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (disposable[0] != null) {
                            disposable[0].dispose();
                        }
                        future.completeExceptionally(new TimeoutException("timeout"));
                    }
                }, timeout);
            } catch (IllegalStateException ex) {
                // if timer or task has been cancelled, do nothing.
            }
        }
        return future;
    }

    private void processData() {
        while (true) {
            /**
             * In vscode debug protocol, the content length represents the
             * message's byte length with utf8 format.
             */
            if (this.contentLength >= 0) {
                if (this.rawData.length() >= this.contentLength) {
                    byte[] buf = this.rawData.removeFirst(this.contentLength);
                    this.contentLength = -1;
                    String messageData = new String(buf, PROTOCOL_ENCODING);
                    try {
                        Messages.ProtocolMessage message = JsonUtils.fromJson(messageData, Messages.ProtocolMessage.class);

                        logger.fine(String.format("\n[%s]\n%s", message.type, messageData));

                        if (message.type.equals("request")) {
                            Messages.Request request = JsonUtils.fromJson(messageData, Messages.Request.class);
                            requestSubject.onNext(request);
                        } else if (message.type.equals("response")) {
                            Messages.Response response = JsonUtils.fromJson(messageData, Messages.Response.class);
                            responseSubject.onNext(response);
                        }
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, String.format("Error parsing message: %s", ex.toString()), ex);
                    }

                    continue;
                }
            }

            String rawMessage = this.rawData.getString(PROTOCOL_ENCODING);
            int idx = rawMessage.indexOf(TWO_CRLF);
            if (idx != -1) {
                Matcher matcher = CONTENT_LENGTH_MATCHER.matcher(rawMessage);
                if (matcher.find()) {
                    this.contentLength = Integer.parseInt(matcher.group(1));
                    int headerByteLength = rawMessage.substring(0, idx + TWO_CRLF.length())
                            .getBytes(PROTOCOL_ENCODING).length;
                    this.rawData.removeFirst(headerByteLength); // Remove the header from the raw message.
                    continue;
                }
            }

            break;
        }
    }

    protected abstract void dispatchRequest(Messages.Request request);

    class ByteBuffer {
        private byte[] buffer;

        public ByteBuffer() {
            this.buffer = new byte[0];
        }

        public int length() {
            return this.buffer.length;
        }

        public String getString(Charset cs) {
            return new String(this.buffer, cs);
        }

        public void append(byte[] b) {
            append(b, b.length);
        }

        public void append(byte[] b, int length) {
            byte[] newBuffer = new byte[this.buffer.length + length];
            System.arraycopy(buffer, 0, newBuffer, 0, this.buffer.length);
            System.arraycopy(b, 0, newBuffer, this.buffer.length, length);
            this.buffer = newBuffer;
        }

        public byte[] removeFirst(int n) {
            byte[] b = new byte[n];
            System.arraycopy(this.buffer, 0, b, 0, n);
            byte[] newBuffer = new byte[this.buffer.length - n];
            System.arraycopy(this.buffer, n, newBuffer, 0, this.buffer.length - n);
            this.buffer = newBuffer;
            return b;
        }
    }
}
