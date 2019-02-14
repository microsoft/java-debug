/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.DisconnectArguments;

public class DisconnectRequestWithoutDebuggingHandler extends AbstractDisconnectRequestHandler {

    @Override
    public void destroyDebugSession(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        DisconnectArguments disconnectArguments = (DisconnectArguments) arguments;
        Process debuggeeProcess = context.getDebuggeeProcess();
        if (debuggeeProcess != null && disconnectArguments.terminateDebuggee) {
            debuggeeProcess.destroy();
        }
    }
}
