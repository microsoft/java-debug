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

import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.protocol.AbstractProtocolServer;
import com.microsoft.java.debug.core.protocol.Messages;

public class ProtocolServer extends AbstractProtocolServer {
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
        debugAdapter = new DebugAdapter((debugEvent, willSendLater) -> {
            // If the protocolServer has been stopped, it'll no longer receive any event.
            if (!terminateSession) {
                if (willSendLater) {
                    sendEventLater(debugEvent.type, debugEvent);
                } else {
                    sendEvent(debugEvent.type, debugEvent);
                }
            }
        }, response -> {
            sendMessage(response);
        }, context);
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    @Override
    public void start() {
        usageDataSession.reportStart();
        super.start();
    }

    /**
     * Sets terminateSession flag to true. And the dispatcher loop will be terminated after current dispatching operation finishes.
     */
    @Override
    public void stop() {
        usageDataSession.reportStop();
        super.stop();
        usageDataSession.submitUsageData();
    }

    @Override
    protected void dispatchRequest(Messages.Request request) {
        Messages.Response response = debugAdapter.dispatchRequest(request);
        if (request.command.equals("disconnect")) {
            stop();
        }
        sendMessage(response);
        usageDataSession.recordResponse(response);
    }

}
