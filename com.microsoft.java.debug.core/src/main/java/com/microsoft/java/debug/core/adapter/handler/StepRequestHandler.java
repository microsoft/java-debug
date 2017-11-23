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

import com.microsoft.java.debug.core.DebugEvent;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.StepArguments;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.StepRequest;

public class StepRequestHandler implements IDebugRequestHandler {
    private Map<Long, ThreadState> threadStates;

    public StepRequestHandler() {
        threadStates = new HashMap<>();
    }

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.CONFIGURATIONDONE, Command.STEPIN, Command.STEPOUT, Command.NEXT);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Debug Session doesn't exist.");
        }

        if (command == Command.CONFIGURATIONDONE) {
            context.getDebugSession().getEventHub().stepEvents().subscribe(debugEvent -> {
                handleDebugEvent(debugEvent, context.getDebugSession(), context);
            });
        } else {
            long threadId = ((StepArguments) arguments).threadId;
            ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), threadId);
            if (thread != null) {
                setPendingStepKind(thread, command);
                setOriginalStackDepth(thread);
                setOriginalStepLocation(thread);
                if (command == Command.STEPIN) {
                    DebugUtility.stepInto(thread, context.getDebugSession().getEventHub(), context.getDebugFilters().stepFilters);
                } else if (command == Command.STEPOUT) {
                    DebugUtility.stepOut(thread, context.getDebugSession().getEventHub(), context.getDebugFilters().stepFilters);
                } else {
                    DebugUtility.stepOver(thread, context.getDebugSession().getEventHub(), context.getDebugFilters().stepFilters);
                }
                ThreadsRequestHandler.checkThreadRunningAndRecycleIds(thread, context);
            }
        }

        return CompletableFuture.completedFuture(response);
    }

    private void handleDebugEvent(DebugEvent debugEvent, IDebugSession debugSession, IDebugAdapterContext context) {
        StepEvent event = (StepEvent) debugEvent.event;
        ThreadReference thread = event.thread();
        debugEvent.shouldResume = false;
        if (context.getDebugFilters() != null) {
            if (getPendingStepKind(thread) == Command.STEPIN) {
                // Check if the step into operation stepped through the filtered code and stopped at an un-filtered location.
                if (getOriginalStackDepth(thread) + 1 < DebugUtility.getFrameCount(thread)) {
                    // Create another stepOut request to return back where we started the step into.
                    DebugUtility.stepOut(thread, debugSession.getEventHub(), context.getDebugFilters().stepFilters);
                    return;
                }
                // If the ending step location is filtered, or same as the original location where the step into operation is originated,
                // do another step of the same kind.
                if (shouldFilterLocation(thread, context) || shouldDoExtraStepInto(thread)) {
                    DebugUtility.stepInto(thread, debugSession.getEventHub(), context.getDebugFilters().stepFilters);
                    return;
                }
            }
        }
        context.sendEvent(new Events.StoppedEvent("step", thread.uniqueID()));
    }

    /**
     * Return true if the StepEvent's location is a Method that the user has indicated to filter.
     */
    private boolean shouldFilterLocation(ThreadReference thread, IDebugAdapterContext context) {
        Location originalLocation = getOriginalStepLocation(thread);
        Location currentLocation = null;
        StackFrame topFrame = DebugUtility.getTopFrame(thread);
        if (topFrame != null) {
            currentLocation = topFrame.location();
        }
        if (originalLocation == null || currentLocation == null) {
            return false;
        }
        return !shouldFilterMethod(originalLocation.method(), context) && shouldFilterMethod(currentLocation.method(), context);
    }

    private boolean shouldFilterMethod(Method method, IDebugAdapterContext context) {
        if ((context.getDebugFilters().skipStaticInitializers && method.isStaticInitializer())
                || (context.getDebugFilters().skipSynthetics && method.isSynthetic())
                || (context.getDebugFilters().skipConstructors && method.isConstructor())) {
            return true;
        }
        return false;
    }

    /**
     * Check if the current top stack is same as the original top stack.
     */
    private boolean shouldDoExtraStepInto(ThreadReference thread) {
        if (getOriginalStackDepth(thread) != DebugUtility.getFrameCount(thread)) {
            return false;
        }
        Location originalLocation = getOriginalStepLocation(thread);
        if (originalLocation == null) {
            return false;
        }
        Location currentLocation = DebugUtility.getTopFrame(thread).location();
        Method originalMethod = originalLocation.method();
        Method currentMethod = currentLocation.method();
        if (!originalMethod.equals(currentMethod)) {
            return false;
        }
        if (originalLocation.lineNumber() != currentLocation.lineNumber()) {
            return false;
        }
        return true;
    }

    private ThreadState getThreadState(ThreadReference thread) {
        if (thread  == null) {
            return null;
        }
        long threadId = thread.uniqueID();
        if (!threadStates.containsKey(threadId)) {
            threadStates.put(threadId, new ThreadState());
        }
        return threadStates.get(threadId);
    }

    private void setPendingStepKind(ThreadReference thread, Command kind) {
        ThreadState state = getThreadState(thread);
        if (state != null) {
            state.pendingStepKind = kind;
        }
    }

    private Command getPendingStepKind(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state == null) {
            return Command.UNSUPPORTED;
        }
        return state.pendingStepKind;
    }

    private void setOriginalStackDepth(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state != null) {
            state.stackDepth = DebugUtility.getFrameCount(thread);
        }
    }

    private int getOriginalStackDepth(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state == null) {
            return -1;
        }
        return state.stackDepth;
    }

    private void setOriginalStepLocation(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state != null) {
            StackFrame topFrame = DebugUtility.getTopFrame(thread);
            if (topFrame != null) {
                state.stepLocation = topFrame.location();
            }
        }
    }

    private Location getOriginalStepLocation(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state != null) {
            return state.stepLocation;
        }
        return null;
    }

    class ThreadState {
        Command pendingStepKind;
        StepRequest pendingStepRequest = null;
        int stackDepth = -1;
        Location stepLocation = null;
    }
}
