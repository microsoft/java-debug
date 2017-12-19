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

package com.microsoft.java.debug.core.adapter.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.ScopesArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.ThreadReference;

public class ScopesRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SCOPES);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        ScopesArguments scopesArgs = (ScopesArguments) arguments;
        List<Types.Scope> scopes = new ArrayList<>();
        StackFrameReference stackFrameReference = (StackFrameReference) context.getRecyclableIdPool().getObjectById(scopesArgs.frameId);
        if (stackFrameReference == null) {
            response.body = new Responses.ScopesResponseBody(scopes);
            return CompletableFuture.completedFuture(response);
        }
        ThreadReference thread = stackFrameReference.getThread();
        VariableProxy localScope = new VariableProxy(thread, "Local", stackFrameReference);
        int localScopeId = context.getRecyclableIdPool().addObject(thread.uniqueID(), localScope);
        scopes.add(new Types.Scope(localScope.getScope(), localScopeId, false));

        response.body = new Responses.ScopesResponseBody(scopes);
        return CompletableFuture.completedFuture(response);
    }
}
