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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.StackFrameUtility;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Events.UserNotificationEvent.NotificationType;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.RestartFrameArguments;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.request.StepRequest;

/**
 * Support Eclipse's `Drop To Frame` action, which is restartFrame in VSCode's
 * debug.
 */
public class RestartFrameHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Requests.Command.RESTARTFRAME);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        RestartFrameArguments restartFrameArgs = (RestartFrameArguments) arguments;
        StackFrameReference stackFrameReference = (StackFrameReference) context.getRecyclableIdPool().getObjectById(restartFrameArgs.frameId);

        if (stackFrameReference == null) {
            throw AdapterUtils.createCompletionException(
                String.format("RestartFrame: cannot find the stack frame with frameID %s", restartFrameArgs.frameId),
                ErrorCode.RESTARTFRAME_FAILURE);
        }

        if (canRestartFrame(context, stackFrameReference)) {
            try {
                ThreadReference reference = stackFrameReference.getThread();
                popStackFrames(context, stackFrameReference);
                stepInto(context, reference);
            } catch (DebugException de) {
                context.getProtocolServer().sendEvent(new Events.UserNotificationEvent(NotificationType.ERROR, de.getMessage()));
                throw AdapterUtils.createCompletionException(
                    String.format("Failed to restart stack frame. Reason: %s", de.getMessage()),
                    ErrorCode.RESTARTFRAME_FAILURE,
                    de);
            }
            return CompletableFuture.completedFuture(response);
        } else {
            context.getProtocolServer().sendEvent(new Events.UserNotificationEvent(NotificationType.ERROR, "Current stack frame doesn't support restart."));
            throw AdapterUtils.createCompletionException("Current stack frame doesn't support restart.", ErrorCode.RESTARTFRAME_FAILURE);
        }
    }

    private boolean canRestartFrame(IDebugAdapterContext context, StackFrameReference frameReference) {
        if (!context.getDebugSession().getVM().canPopFrames()) {
            return false;
        }
        ThreadReference reference = frameReference.getThread();
        int totalFrames;
        try {
            totalFrames = reference.frameCount();
        } catch (IncompatibleThreadStateException e) {
            return false;
        }

        // The frame cannot be the bottom one of the call stack:
        if (totalFrames <= frameReference.getDepth() + 1) {
            return false;
        }

        StackFrame[] frames = context.getStackFrameManager().reloadStackFrames(reference, 0, frameReference.getDepth() + 2);
        if (frames.length == 0) {
            return false;
        }

        // Cannot restart frame involved with native call stacks:
        for (int i = 0; i <= frameReference.getDepth() + 1; i++) {
            if (StackFrameUtility.isNative(frames[i])) {
                return false;
            }
        }
        return true;
    }

    private void popStackFrames(IDebugAdapterContext context, StackFrameReference stackFrameRef) throws DebugException {
        StackFrame frame = context.getStackFrameManager().getStackFrame(stackFrameRef);
        if (frame == null) {
            return;
        }
        StackFrameUtility.pop(frame);
    }

    private void stepInto(IDebugAdapterContext context, ThreadReference thread) {
        StepRequest request = DebugUtility.createStepIntoRequest(thread, context.getStepFilters().allowClasses, context.getStepFilters().skipClasses);
        context.getDebugSession().getEventHub().stepEvents().filter(debugEvent -> request.equals(debugEvent.event.request())).take(1).subscribe(debugEvent -> {
            debugEvent.shouldResume = false;
            // Have to send two events to keep the UI sync with the step in operations:
            context.getProtocolServer().sendEvent(new Events.ContinuedEvent(thread.uniqueID()));
            context.getProtocolServer().sendEvent(new Events.StoppedEvent("restartframe", thread.uniqueID()));
        });
        request.enable();
        thread.resume();
    }
}