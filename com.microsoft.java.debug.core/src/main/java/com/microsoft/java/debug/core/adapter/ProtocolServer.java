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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.protocol.AbstractProtocolServer;
import com.microsoft.java.debug.core.protocol.Events.DebugEvent;
import com.microsoft.java.debug.core.protocol.Events.StoppedEvent;
import com.microsoft.java.debug.core.protocol.Messages;
import com.sun.jdi.VMDisconnectedException;

public class ProtocolServer extends AbstractProtocolServer {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private IDebugAdapter debugAdapter;
    private UsageDataSession usageDataSession = new UsageDataSession();

    private Object lock = new Object();
    private boolean isDispatchingRequest = false;
    private ConcurrentLinkedQueue<DebugEvent> eventQueue = new ConcurrentLinkedQueue<>();

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
        super(input, output);
        debugAdapter = new DebugAdapter(this, context);
    }

    /**
     * Constructs a protocol server instance based on the given input stream and output stream.
     * @param input
     *              the input stream
     * @param output
     *              the output stream
     * @param debugAdapterFactory
     *              factory to create debug adapter that implements DAP communication
     */
    public ProtocolServer(InputStream input, OutputStream output, IDebugAdapterFactory debugAdapterFactory) {
        super(input, output);
        debugAdapter = debugAdapterFactory.create(this);
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    @Override
    public void run() {
        usageDataSession.reportStart();
        super.run();
        usageDataSession.reportStop();
        usageDataSession.submitUsageData();
    }

    @Override
    public void sendResponse(Messages.Response response) {
        usageDataSession.recordResponse(response);
        super.sendResponse(response);
    }

    @Override
    public CompletableFuture<Messages.Response> sendRequest(Messages.Request request) {
        usageDataSession.recordRequest(request);
        return super.sendRequest(request);
    }

    @Override
    public CompletableFuture<Messages.Response> sendRequest(Messages.Request request, long timeout) {
        usageDataSession.recordRequest(request);
        return super.sendRequest(request, timeout);
    }

    @Override
    public void sendEvent(DebugEvent event) {
        // See the two bugs https://github.com/Microsoft/java-debug/issues/134 and https://github.com/Microsoft/vscode/issues/58327,
        // it requires the java-debug to send the StoppedEvent after ContinueResponse/StepResponse is received by DA.
        if (event instanceof StoppedEvent) {
            sendEventLater(event);
        } else {
            super.sendEvent(event);
        }

    }

    /**
     * If the the dispatcher is idle, then send the event to the DA immediately.
     * Else add the new event to an eventQueue first and send them when dispatcher becomes idle again.
     */
    private void sendEventLater(DebugEvent event) {
        synchronized (lock) {
            if (this.isDispatchingRequest) {
                this.eventQueue.offer(event);
            } else {
                super.sendEvent(event);
            }
        }
    }

    @Override
    protected void dispatchRequest(Messages.Request request) {
        usageDataSession.recordRequest(request);
        try {
            synchronized (lock) {
                this.isDispatchingRequest = true;
            }

            debugAdapter.dispatchRequest(request).thenCompose((response) -> {
                CompletableFuture<Void> future = new CompletableFuture<>();
                if (response != null) {
                    sendResponse(response);
                    future.complete(null);
                } else {
                    future.completeExceptionally(new DebugException("The request dispatcher should not return null response.",
                            ErrorCode.UNKNOWN_FAILURE.getId()));
                }
                return future;
            }).exceptionally((ex) -> {
                Messages.Response response = new Messages.Response(request.seq, request.command);
                if (ex instanceof CompletionException && ex.getCause() != null) {
                    ex = ex.getCause();
                }

                if (ex instanceof VMDisconnectedException) {
                    // mark it success to avoid reporting error on VSCode.
                    response.success = true;
                    sendResponse(response);
                } else {
                    String exceptionMessage = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                    ErrorCode errorCode = ex instanceof DebugException ? ErrorCode.parse(((DebugException) ex).getErrorCode()) : ErrorCode.UNKNOWN_FAILURE;
                    boolean isUserError = ex instanceof DebugException && ((DebugException) ex).isUserError();
                    if (isUserError) {
                        usageDataSession.recordUserError(errorCode);
                    } else {
                        logger.log(Level.SEVERE, String.format("[error response][%s]: %s", request.command, exceptionMessage), ex);
                    }

                    sendResponse(AdapterUtils.setErrorResponse(response,
                            errorCode,
                            exceptionMessage));
                }
                return null;
            }).join();
        } finally {
            synchronized (lock) {
                this.isDispatchingRequest = false;
            }

            while (this.eventQueue.peek() != null) {
                super.sendEvent(this.eventQueue.poll());
            }
        }
    }
}
