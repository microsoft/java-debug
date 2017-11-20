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
        this.debugAdapter = new DebugAdapter(this, context);
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    public void start() {
        usageDataSession.reportStart();
        super.start();
        usageDataSession.reportStop();
        usageDataSession.submitUsageData();
    }

    @Override
    public void sendMessage(Messages.ProtocolMessage message) {
        super.sendMessage(message);
        if (message instanceof Messages.Response) {
            usageDataSession.recordResponse((Messages.Response) message);
        } else if (message instanceof Messages.Request) {
            usageDataSession.recordRequest((Messages.Request) message);
        }
    }

    protected void dispatchRequest(Messages.Request request) {
        usageDataSession.recordRequest(request);
        this.debugAdapter.dispatchRequest(request);
    }

}
