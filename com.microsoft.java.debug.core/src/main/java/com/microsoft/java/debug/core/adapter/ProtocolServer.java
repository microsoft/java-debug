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
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.protocol.AbstractProtocolServer;
import com.microsoft.java.debug.core.protocol.Messages;
import com.sun.jdi.VMDisconnectedException;

public class ProtocolServer extends AbstractProtocolServer {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private IDebugAdapter debugAdapter;
    private UsageDataSession usageDataSession = new UsageDataSession();

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
        this.debugAdapter = new DebugAdapter((message) -> {
            sendMessage(message);
        }, context);
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    public void run() {
        usageDataSession.reportStart();
        super.run();
        usageDataSession.reportStop();
        usageDataSession.submitUsageData();
    }

    @Override
    protected void sendMessage(Messages.ProtocolMessage message) {
        super.sendMessage(message);
        if (message instanceof Messages.Response) {
            usageDataSession.recordResponse((Messages.Response) message);
        } else if (message instanceof Messages.Request) {
            usageDataSession.recordRequest((Messages.Request) message);
        }
    }

    protected void dispatchRequest(Messages.Request request) {
        usageDataSession.recordRequest(request);
        this.debugAdapter.dispatchRequest(request).whenComplete((response, ex) -> {
            if (response != null) {
                sendMessage(response);
            } else {
                response = new Messages.Response();
                response.request_seq = request.seq;
                response.command = request.command;
                response.success = false;

                if (ex != null) {
                    if (ex instanceof CompletionException && ex.getCause() != null) {
                        ex = ex.getCause();
                    }

                    if (ex instanceof VMDisconnectedException) {
                        //response.success = true;
                        sendMessage(response);
                    } else {
                        sendMessage(AdapterUtils.setErrorResponse(response,
                                ErrorCode.UNKNOWN_FAILURE,
                                ex.getMessage() != null ? ex.getMessage() : ex.toString()));
                    }
                } else {
                    logger.log(Level.SEVERE, "The request dispatcher should not return null response.");
                    sendMessage(AdapterUtils.setErrorResponse(response,
                            ErrorCode.UNKNOWN_FAILURE,
                            "The request dispatcher should not return null response."));
                }
            }
        }).whenComplete((r, e) -> {
            if (e != null) {
                logger.log(Level.SEVERE, "Unexpected exception occurs when sending message to VSCode: %s" + e.getMessage());
            }
        });
    }

}
