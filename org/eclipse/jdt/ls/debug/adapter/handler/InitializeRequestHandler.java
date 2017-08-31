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

import org.eclipse.jdt.ls.debug.adapter.Events;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.Messages;
import org.eclipse.jdt.ls.debug.adapter.Requests;
import org.eclipse.jdt.ls.debug.adapter.Types;  

public class InitializeRequestHandler implements IDebugRequestHandler {
    @Override
    public List<Requests.Command> getTargetCommands() {
        return Arrays.asList(Requests.Command.INITIALIZE);
    }

    @Override
    public void handle(Requests.Command command, Requests.Arguments argument, Messages.Response response,
                       IDebugAdapterContext context) {
        Requests.InitializeArguments initializeArguments = (Requests.InitializeArguments) argument;
        context.setClientLinesStartAt1(initializeArguments.linesStartAt1);
        String pathFormat = initializeArguments.pathFormat;
        if (pathFormat != null) {
            switch (pathFormat) {
                case "uri":
                    context.setClientPathsAreUri(true);
                    break;
                default:
                    context.setClientPathsAreUri(false);
            }
        }

        // Send an InitializedEvent
        context.sendEventAsync(new Events.InitializedEvent());

        Types.Capabilities caps = new Types.Capabilities();
        caps.supportsConfigurationDoneRequest = true;
        caps.supportsHitConditionalBreakpoints = true;
        caps.supportsSetVariable = true;
        caps.supportTerminateDebuggee = true;
        Types.ExceptionBreakpointFilter[] exceptionFilters = {
            Types.ExceptionBreakpointFilter.UNCAUGHT_EXCEPTION_FILTER,
            Types.ExceptionBreakpointFilter.CAUGHT_EXCEPTION_FILTER,
        };
        caps.exceptionBreakpointFilters = exceptionFilters;
        response.body = caps;
    }
}
