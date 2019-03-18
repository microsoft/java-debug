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

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;

public class ProcessConsole {
    private Process process;
    private String name;
    private Charset encoding;
    private PublishSubject<String> stdoutSubject = PublishSubject.<String>create();
    private PublishSubject<String> stderrSubject = PublishSubject.<String>create();
    private Thread stdoutThread = null;
    private Thread stderrThread = null;

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
                monitor(process.getInputStream(), stdoutSubject);
            }
        };
        stdoutThread.setDaemon(true);
        stdoutThread.start();

        this.stderrThread = new Thread(this.name + " Stderr Handler") {
            public void run() {
                monitor(process.getErrorStream(), stderrSubject);
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

    public Disposable onStdout(Consumer<String> callback) {
        return stdoutSubject.subscribe(callback);
    }

    public Disposable onStderr(Consumer<String> callback) {
        return stderrSubject.subscribe(callback);
    }

    private void monitor(InputStream input, PublishSubject<String> subject) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding));
        final int BUFFERSIZE = 4096;
        char[] buffer = new char[BUFFERSIZE];
        while (true) {
            try {
                if (Thread.interrupted()) {
                    subject.onComplete();
                    return;
                }
                int read = reader.read(buffer, 0, BUFFERSIZE);
                if (read == -1) {
                    subject.onComplete();
                    return;
                }

                subject.onNext(new String(buffer, 0, read));
            } catch (IOException e) {
                subject.onError(e);
                return;
            }
        }
    }
}
