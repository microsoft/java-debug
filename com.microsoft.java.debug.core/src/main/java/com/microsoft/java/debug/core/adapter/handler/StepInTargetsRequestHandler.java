/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Gayan Perera - initial API and implementation
*******************************************************************************/
package com.microsoft.java.debug.core.adapter.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider.MethodInvocation;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.StepInTargetsArguments;
import com.microsoft.java.debug.core.protocol.Responses.StepInTargetsResponse;
import com.microsoft.java.debug.core.protocol.Types.StepInTarget;
import com.sun.jdi.StackFrame;

public class StepInTargetsRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.STEPIN_TARGETS);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        final StepInTargetsArguments stepInTargetsArguments = (StepInTargetsArguments) arguments;

        final int frameId = stepInTargetsArguments.frameId;
        return CompletableFuture.supplyAsync(() -> {
            response.body = new StepInTargetsResponse(
                    findFrame(frameId, context).map(f -> findTargets(f.thread().uniqueID(), f, context))
                            .orElse(Collections.emptyList()).toArray(StepInTarget[]::new));
            return response;
        });
    }

    private Optional<StackFrame> findFrame(int frameId, IDebugAdapterContext context) {
        Object object = context.getRecyclableIdPool().getObjectById(frameId);
        if (object instanceof StackFrameReference) {
            return Optional.of(context.getStackFrameManager().getStackFrame((StackFrameReference) object));
        }
        return Optional.empty();
    }

    private List<StepInTarget> findTargets(long threadId, StackFrame stackframe, IDebugAdapterContext context) {
        ISourceLookUpProvider sourceLookUpProvider = context.getProvider(ISourceLookUpProvider.class);
        List<MethodInvocation> invocations = sourceLookUpProvider.findMethodInvocations(stackframe);
        if (invocations.isEmpty()) {
            return Collections.emptyList();
        }

        List<StepInTarget> targets = new ArrayList<>(invocations.size());
        for (MethodInvocation methodInvocation : invocations) {
            int id = context.getRecyclableIdPool().addObject(threadId, methodInvocation);
            StepInTarget target = new StepInTarget(id, methodInvocation.expression);
            target.column = methodInvocation.columnStart;
            target.endColumn = methodInvocation.columnEnd;
            target.line = methodInvocation.lineStart;
            target.endLine = methodInvocation.lineEnd;
            targets.add(target);
        }
        return targets;
    }
}
