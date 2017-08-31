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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.Requests.ScopesArguments;
import org.eclipse.jdt.ls.debug.adapter.Responses;
import org.eclipse.jdt.ls.debug.adapter.Types;
import org.eclipse.jdt.ls.debug.adapter.variables.JdiObjectProxy;
import org.eclipse.jdt.ls.debug.adapter.variables.VariableProxy;

import com.sun.jdi.StackFrame;

public class ScopesRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SCOPES);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        ScopesArguments scopesArgs = (ScopesArguments) arguments;
        List<Types.Scope> scopes = new ArrayList<>();
        JdiObjectProxy<StackFrame> stackFrameProxy = (JdiObjectProxy<StackFrame>) context.getRecyclableIdPool().getObjectById(scopesArgs.frameId);
        if (stackFrameProxy == null) {
            response.body = new Responses.ScopesResponseBody(scopes);
            return;
        }
        StackFrame stackFrame = stackFrameProxy.getProxiedObject();
        VariableProxy localScope = new VariableProxy(stackFrame.thread().uniqueID(), "Local", stackFrame);
        int localScopeId = context.getRecyclableIdPool().addObject(stackFrame.thread().uniqueID(), localScope);
        scopes.add(new Types.Scope(localScope.getScope(), localScopeId, false));

        response.body = new Responses.ScopesResponseBody(scopes);
    }

}
