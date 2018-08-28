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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.ICompletionsProvider;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.CompletionsArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types.CompletionItem;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ThreadReference;

public class CompletionsHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Requests.Command.COMPLETIONS);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        CompletionsArguments completionsArgs = (CompletionsArguments) arguments;
        // completions should be illegal when frameId is zero, it is sent when the program is running, while during running we cannot resolve
        // the completion candidates
        if (completionsArgs.frameId == 0) {
            response.body = new ArrayList<>();
            return CompletableFuture.completedFuture(response);
        }
        StackFrameReference stackFrameReference = (StackFrameReference) context.getRecyclableIdPool().getObjectById(completionsArgs.frameId);

        if (stackFrameReference == null) {
            throw AdapterUtils.createCompletionException(
                String.format("Completions: cannot find the stack frame with frameID %s", completionsArgs.frameId),
                ErrorCode.COMPLETIONS_FAILURE
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ICompletionsProvider completionsProvider = context.getProvider(ICompletionsProvider.class);
                if (completionsProvider != null) {
                    ThreadReference thread = stackFrameReference.getThread();

                    List<CompletionItem> res = completionsProvider.codeComplete(thread.frame(stackFrameReference.getDepth()), completionsArgs.text,
                            completionsArgs.line, completionsArgs.column);
                    response.body = new Responses.CompletionsResponseBody(res);
                }
                return response;
            } catch (IncompatibleThreadStateException e) {
                throw AdapterUtils.createCompletionException(
                    String.format("Cannot provide code completions because of %s.", e.toString()),
                    ErrorCode.COMPLETIONS_FAILURE,
                    e
                );
            }
        });
    }
}
