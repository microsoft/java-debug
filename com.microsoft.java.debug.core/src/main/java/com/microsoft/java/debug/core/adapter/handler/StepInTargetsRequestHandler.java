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

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
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
import com.microsoft.java.debug.core.protocol.Types.Source;
import com.microsoft.java.debug.core.protocol.Types.StepInTarget;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;

public class StepInTargetsRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

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
                    findFrame(frameId, context).map(f -> findTargets(f, context))
                            .orElse(Collections.emptyList()).toArray(StepInTarget[]::new));
            return response;
        });
    }

    private Optional<StackFrameReference> findFrame(int frameId, IDebugAdapterContext context) {
        Object object = context.getRecyclableIdPool().getObjectById(frameId);
        if (object instanceof StackFrameReference) {
            return Optional.of((StackFrameReference) object);
        }
        return Optional.empty();
    }

    private List<StepInTarget> findTargets(StackFrameReference frameReference, IDebugAdapterContext context) {
        StackFrame stackframe = context.getStackFrameManager().getStackFrame(frameReference);
        if (stackframe == null) {
            return Collections.emptyList();
        }

        Source source = frameReference.getSource() == null ? findSource(stackframe, context) : frameReference.getSource();
        if (source == null) {
            return Collections.emptyList();
        }

        String sourceUri = AdapterUtils.convertPath(source.path, AdapterUtils.isUri(source.path), true);
        if (sourceUri == null) {
            return Collections.emptyList();
        }

        ISourceLookUpProvider sourceLookUpProvider = context.getProvider(ISourceLookUpProvider.class);
        List<MethodInvocation> invocations = sourceLookUpProvider.findMethodInvocations(sourceUri, stackframe.location().lineNumber());
        if (invocations.isEmpty()) {
            return Collections.emptyList();
        }

        long threadId = stackframe.thread().uniqueID();
        List<StepInTarget> targets = new ArrayList<>(invocations.size());
        for (MethodInvocation methodInvocation : invocations) {
            int id = context.getRecyclableIdPool().addObject(threadId, methodInvocation);
            StepInTarget target = new StepInTarget(id, methodInvocation.expression);
            target.column = AdapterUtils.convertColumnNumber(methodInvocation.columnStart,
                context.isDebuggerColumnsStartAt1(), context.isClientColumnsStartAt1());
            target.endColumn = AdapterUtils.convertColumnNumber(methodInvocation.columnEnd,
                context.isDebuggerColumnsStartAt1(), context.isClientColumnsStartAt1());
            target.line = AdapterUtils.convertLineNumber(methodInvocation.lineStart,
                context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
            target.endLine = AdapterUtils.convertLineNumber(methodInvocation.lineEnd,
                context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
            targets.add(target);
        }

        // TODO remove the executed method calls.
        return targets;
    }

    private Source findSource(StackFrame frame, IDebugAdapterContext context) {
        ReferenceType declaringType = frame.location().declaringType();
        String typeName = declaringType.name();
        String sourceName = null;
        String sourcePath = null;
        try {
            // When the .class file doesn't contain source information in meta data,
            // invoking ReferenceType#sourceName() would throw AbsentInformationException.
            sourceName = declaringType.sourceName();
            sourcePath = declaringType.sourcePaths(null).get(0);
        } catch (AbsentInformationException e) {
            String enclosingType = AdapterUtils.parseEnclosingType(typeName);
            sourceName = enclosingType.substring(enclosingType.lastIndexOf('.') + 1) + ".java";
            sourcePath = enclosingType.replace('.', File.separatorChar) + ".java";
        }

        try {
            return StackTraceRequestHandler.convertDebuggerSourceToClient(typeName, sourceName, sourcePath, context);
        } catch (URISyntaxException e) {
            logger.log(Level.SEVERE, "Failed to resolve the source info of the stack frame.", e);
        }

        return  null;
    }
}
