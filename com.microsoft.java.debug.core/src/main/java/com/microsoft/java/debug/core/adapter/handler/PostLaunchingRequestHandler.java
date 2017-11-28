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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.LaunchBaseArguments;

public class PostLaunchingRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.LAUNCH, Command.ATTACH);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        if (response.success) {
            LaunchBaseArguments baseArguments = (LaunchBaseArguments) arguments;

            context.setSourcePaths(baseArguments.sourcePaths);

            Map<String, Object> options = new HashMap<>();
            options.put(Constants.DEBUGGEE_ENCODING, context.getDebuggeeEncoding());
            if (baseArguments.projectName != null) {
                options.put(Constants.PROJECTNAME, baseArguments.projectName);
            }
            ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
            sourceProvider.initialize(context.getDebugSession(), options);

            // Send an InitializedEvent to indicate that the debugger is ready to accept configuration requests
            // (e.g. SetBreakpointsRequest, SetExceptionBreakpointsRequest).
            context.sendEvent(new Events.InitializedEvent());
        }
        return CompletableFuture.completedFuture(response);
    }

}
