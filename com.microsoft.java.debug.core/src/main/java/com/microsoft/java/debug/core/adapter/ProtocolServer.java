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

package com.microsoft.java.debug.core.adapter;

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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.java.debug.core.Logger;

public class ProtocolServer {
    private static final int BUFFER_SIZE = 4096;
    private static final String TWO_CRLF = "\r\n\r\n";
    private static final Pattern CONTENT_LENGTH_MATCHER = Pattern.compile("Content-Length: (\\d+)");
    private static final Charset PROTOCOL_ENCODING = StandardCharsets.UTF_8; // vscode protocol uses UTF-8 as encoding format.

    private Reader reader;
    private Writer writer;

    private ByteBuffer rawData;
    private boolean terminateSession = false;
    private int contentLength = -1;
    private AtomicInteger sequenceNumber = new AtomicInteger(1);

    private boolean isDispatchingData;
    private ConcurrentLinkedQueue<Messages.Event> eventQueue;

    private IDebugAdapter debugAdapter;

    /**
     * Constructs a protocol server instance based on the given input stream and output stream.
     * @param input
     *              the input stream
     * @param output
     *              the output stream
     * @param context
     *              provider context for a series of provider implementation
     */
    public ProtocolServer(InputStream input, OutputStream output, IProviderContext context) {
        this.reader = new BufferedReader(new InputStreamReader(input, PROTOCOL_ENCODING));
        this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(output, PROTOCOL_ENCODING)));
        this.contentLength = -1;
        this.rawData = new ByteBuffer();
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.debugAdapter = new DebugAdapter((debugEvent, willSendLater) -> {
            // If the protocolServer has been stopped, it'll no longer receive any event.
            if (!terminateSession) {
                if (willSendLater) {
                    this.sendEventLater(debugEvent.type, debugEvent);
                } else {
                    this.sendEvent(debugEvent.type, debugEvent);
                }
            }
        }, context);
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    public void start() {
        char[] buffer = new char[BUFFER_SIZE];
        try {
            while (!this.terminateSession) {
                int read = this.reader.read(buffer, 0, BUFFER_SIZE);
                if (read == -1) {
                    break;
                }

                this.rawData.append(new String(buffer, 0, read).getBytes(PROTOCOL_ENCODING));
                this.processData();
            }
        } catch (IOException e) {
            Logger.logException("Read data from io exception", e);
        }
    }

    /**
     * Sets terminateSession flag to true. And the dispatcher loop will be terminated after current dispatching operation finishes.
     */
    public void stop() {
        this.terminateSession = true;
    }

    /**
     * Send event to DA immediately.
     * @param eventType
     *                 event type
     * @param body
     *                 event body
     */
    private void sendEvent(String eventType, Object body) {
        sendMessage(new Messages.Event(eventType, body));
    }

    /**
     * If the the dispatcher is idle, then send the event immediately.
     * Else add the new event to an eventQueue first and send them when dispatcher becomes idle again.
     * @param eventType
     *              event type
     * @param body
     *              event content
     */
    private void sendEventLater(String eventType, Object body) {
        synchronized (this) {
            if (this.isDispatchingData) {
                this.eventQueue.offer(new Messages.Event(eventType, body));
            } else {
                sendMessage(new Messages.Event(eventType, body));
            }
        }
    }

    private void sendMessage(Messages.ProtocolMessage message) {
        message.seq = this.sequenceNumber.getAndIncrement();

        String jsonMessage = JsonUtils.toJson(message);
        byte[] jsonBytes = jsonMessage.getBytes(PROTOCOL_ENCODING);

        String header = String.format("Content-Length: %d%s", jsonBytes.length, TWO_CRLF);
        byte[] headerBytes = header.getBytes(PROTOCOL_ENCODING);

        byte[] data = new byte[headerBytes.length + jsonBytes.length];
        System.arraycopy(headerBytes, 0, data, 0, headerBytes.length);
        System.arraycopy(jsonBytes, 0, data, headerBytes.length, jsonBytes.length);

        String utf8Data = new String(data, PROTOCOL_ENCODING);

        try {
            Logger.logInfo("\n[[RESPONSE]]\n" + new String(data));
            this.writer.write(utf8Data);
            this.writer.flush();
        } catch (IOException e) {
            Logger.logException("Write data to io exception", e);
        }
    }

    private void processData() {
        while (true) {
            /**
             * In vscode debug protocol, the content length represents the message's byte length with utf8 format.
             */
            if (this.contentLength >= 0) {
                if (this.rawData.length() >= this.contentLength) {
                    byte[] buf = this.rawData.removeFirst(this.contentLength);
                    this.contentLength = -1;
                    dispatchRequest(new String(buf, PROTOCOL_ENCODING));
                    continue;
                }
            } else {
                String rawMessage = this.rawData.getString(PROTOCOL_ENCODING);
                int idx = rawMessage.indexOf(TWO_CRLF);
                if (idx != -1) {
                    Matcher matcher = CONTENT_LENGTH_MATCHER.matcher(rawMessage);
                    if (matcher.find()) {
                        this.contentLength = Integer.parseInt(matcher.group(1));
                        int headerByteLength = rawMessage.substring(0, idx + TWO_CRLF.length()).getBytes(PROTOCOL_ENCODING).length;
                        this.rawData.removeFirst(headerByteLength); // Remove the header from the raw message.
                        continue;
                    }
                }
            }
            break;
        }
    }

    private void dispatchRequest(String request) {
        try {
            Logger.logInfo("\n[REQUEST]\n" + request);
            Messages.Request message = JsonUtils.fromJson(request, Messages.Request.class);
            if (message.type.equals("request")) {
                synchronized (this) {
                    this.isDispatchingData = true;
                }

                try {
                    Messages.Response response = this.debugAdapter.dispatchRequest(message);
                    if (message.command.equals("disconnect")) {
                        this.stop();
                    }
                    sendMessage(response);
                } catch (Exception e) {
                    Logger.logException("Dispatch debug protocol error", e);
                }
            }
        } finally {
            synchronized (this) {
                this.isDispatchingData = false;
            }

            while (this.eventQueue.peek() != null) {
                sendMessage(this.eventQueue.poll());
            }
        }
    }

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
