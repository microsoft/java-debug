/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;

public class TerminateRequestHandler implements IDebugRequestHandler {
    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.TERMINATE);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        if (context.isAttached()) {
            return CompletableFuture.completedFuture(response);
        }

        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            ThreadsRequestHandler.resumeAllThreads(context);
            debugSession.terminate();
        } else {
            DebuggeeProcessHelper.terminate(context);
        }
        return CompletableFuture.completedFuture(response);
    }
}
