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

package org.eclipse.jdt.ls.debug.adapter.handler;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.ls.debug.IDebugSession;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.Requests.DisconnectArguments;

public class DisconnectRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.DISCONNECT);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        DisconnectArguments disconnectArguments = (DisconnectArguments) arguments;
        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            if (disconnectArguments.terminateDebuggee && !context.isAttached()) {
                debugSession.terminate();
            } else {
                debugSession.detach();
            }
        }
    }

}
