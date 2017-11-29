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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.protocol.AbstractProtocolServer;
import com.microsoft.java.debug.core.protocol.Messages;

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
        this.debugAdapter = new DebugAdapter(this, context);
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
    protected void dispatchRequest(Messages.Request request) {
        usageDataSession.recordRequest(request);
        this.debugAdapter.dispatchRequest(request).whenComplete((response, ex) -> {
            if (response != null) {
                sendResponse(response);
            } else if (ex != null) {
                logger.log(Level.SEVERE, String.format("DebugSession dispatch exception: %s", ex.toString()), ex);
                sendResponse(AdapterUtils.setErrorResponse(response,
                        ErrorCode.UNKNOWN_FAILURE,
                        ex.getMessage() != null ? ex.getMessage() : ex.toString()));
            } else {
                logger.log(Level.SEVERE, "The request dispatcher should not return null response.");
                sendResponse(AdapterUtils.setErrorResponse(response,
                        ErrorCode.UNKNOWN_FAILURE,
                        "The request dispatcher should not return null response."));
            }
        });
    }

}
