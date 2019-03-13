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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class ProcessConsole {
    private Process process;
    private String name;
    private Charset encoding;
    private PublishSubject<Message> outputSubject = PublishSubject.<Message>create();
    private Thread stdoutThread = null;
    private Thread stderrThread = null;
    private AtomicInteger exit = new AtomicInteger(0);
    private Disposable consumer = null;

    /**
     * constructor.
     */
    public ProcessConsole(Process process) {
        this(process, "Process", StandardCharsets.UTF_8);
    }

    /**
     * Constructor.
     * @param process
     *              the process
     * @param name
     *              the process name
     * @param encoding
     *              the process encoding format
     */
    public ProcessConsole(Process process, String name, Charset encoding) {
        this.process = process;
        this.name = name;
        this.encoding = encoding;
    }

    /**
     * Start two separate threads to monitor the messages from stdout and stderr streams of the target process.
     */
    public void start() {
        this.stdoutThread = new Thread(this.name + " Stdout Handler") {
            public void run() {
                monitor(process.getInputStream(), MessageType.STDOUT);
            }
        };
        stdoutThread.setDaemon(true);
        stdoutThread.start();

        this.stderrThread = new Thread(this.name + " Stderr Handler") {
            public void run() {
                monitor(process.getErrorStream(), MessageType.STDERR);
            }
        };
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    /**
     * Stop the process console handlers.
     */
    public void stop() {
        if (this.stdoutThread != null) {
            this.stdoutThread.interrupt();
            this.stdoutThread = null;
        }

        if (this.stderrThread != null) {
            this.stderrThread.interrupt();
            this.stderrThread = null;
        }
    }

    /**
     * Register a callback to consume the stdout/stderr message from the process.
     */
    public void onData(Consumer<Message> callback) {
        this.consumer = outputSubject.observeOn(Schedulers.newThread()).subscribe(callback);
    }

    private void monitor(InputStream input, MessageType type) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding));
        final int BUFFERSIZE = 4096;
        char[] buffer = new char[BUFFERSIZE];
        while (true) {
            try {
                if (Thread.interrupted()) {
                    break;
                }
                int read = reader.read(buffer, 0, BUFFERSIZE);
                if (read == -1) {
                    break;
                }

                outputSubject.onNext(new Message(new String(buffer, 0, read), type));
            } catch (IOException e) {
                break;
            }
        }

        if (exit.addAndGet(1) == 2) {
            outputSubject.onComplete();
        }
    }

    /**
     * Wait for the process's stdout/stderr message is fully consumed by the debug adapter.
     */
    public void waitFor() {
        // Give the debug adapter additional 3 seconds to handle the process io data.
        waitFor(3);
    }

    /**
     * Wait for the process's stdout/stderr message is fully consumed by the debug adapter.
     * @param timeoutSeconds - The maximum waiting time
     */
    public void waitFor(int timeoutSeconds) {
        int retry = timeoutSeconds * 2;
        while (retry-- > 0) {
            if (this.consumer != null && this.consumer.isDisposed()) {
                break;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                // do nothing.
            }
        }
    }

    public static class Message {
        public String output;
        public MessageType type;

        public Message(String output, MessageType type) {
            this.output = output;
            this.type = type;
        }
    }

    public static enum MessageType {
        STDOUT, STDERR
    }
}
