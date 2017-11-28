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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.ArrayUtils;

import com.microsoft.java.debug.core.Configuration;
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
import com.microsoft.java.debug.core.protocol.Requests.StepFilters;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.request.StepRequest;

public class StepRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
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
            context.getDebugSession().getEventHub().events()
                .filter(debugEvent -> debugEvent.event instanceof StepEvent
                        || debugEvent.event instanceof BreakpointEvent
                        || debugEvent.event instanceof ThreadDeathEvent)
                .subscribe(debugEvent -> {
                    handleDebugEvent(debugEvent, context.getDebugSession(), context);
                });
            return CompletableFuture.completedFuture(response);
        }

        long threadId = ((StepArguments) arguments).threadId;
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), threadId);
        if (thread != null) {
            try {
                setPendingStepType(threadId, command);
                setOriginalStackDepth(threadId, thread.frameCount());
                setOriginalStepLocation(threadId, getTopFrame(thread).location());
                StepRequest stepRequest;
                if (command == Command.STEPIN) {
                    stepRequest = DebugUtility.stepInto(thread, context.getDebugSession().getEventHub(), context.getStepFilters().classNameFilters);
                } else if (command == Command.STEPOUT) {
                    stepRequest = DebugUtility.stepOut(thread, context.getDebugSession().getEventHub(), context.getStepFilters().classNameFilters);
                } else {
                    stepRequest = DebugUtility.stepOver(thread, context.getDebugSession().getEventHub(), context.getStepFilters().classNameFilters);
                }
                setPendingStepRequest(threadId, stepRequest);
                ThreadsRequestHandler.checkThreadRunningAndRecycleIds(thread, context);
            } catch (IncompatibleThreadStateException ex) {
                final String failureMessage = String.format("Failed to step because the thread '%s' is not suspended in the target VM.", thread.name());
                logger.log(Level.SEVERE, failureMessage);
                return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.STEP_FAILURE, failureMessage);
            } catch (IndexOutOfBoundsException ex) {
                final String failureMessage = String.format("Failed to step because the thread '%s' doesn't contain any stack frame", thread.name());
                logger.log(Level.SEVERE, failureMessage);
                return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.STEP_FAILURE, failureMessage);
            }
        }

        return CompletableFuture.completedFuture(response);
    }

    private void handleDebugEvent(DebugEvent debugEvent, IDebugSession debugSession, IDebugAdapterContext context) {
        Event event = debugEvent.event;

        // When a breakpoint occurs, abort any pending step requests from the same thread.
        if (event instanceof BreakpointEvent) {
            long threadId = ((BreakpointEvent) event).thread().uniqueID();
            StepRequest pendingStepRequest = getPendingStepRequest(threadId);
            if (pendingStepRequest != null) {
                DebugUtility.deleteEventRequestSafely(debugSession.getVM().eventRequestManager(), pendingStepRequest);
            }
            removeThreadState(threadId);
        } else if (event instanceof ThreadDeathEvent) {
            long threadId = ((ThreadDeathEvent) event).thread().uniqueID();
            removeThreadState(threadId);
        } else if (event instanceof StepEvent) {
            debugEvent.shouldResume = false;
            ThreadReference thread = ((StepEvent) event).thread();
            long threadId = thread.uniqueID();
            setPendingStepRequest(threadId, null); // clean up the pending status.
            if (isStepFiltersConfigured(context.getStepFilters())) {
                try {
                    if (getPendingStepType(threadId) == Command.STEPIN) {
                        // Check if the step into operation stepped through the filtered code and stopped at an un-filtered location.
                        if (getOriginalStackDepth(threadId) + 1 < thread.frameCount()) {
                            // Create another stepOut request to return back where we started the step into.
                            StepRequest stepRequest = DebugUtility.stepOut(thread, debugSession.getEventHub(), context.getStepFilters().classNameFilters);
                            setPendingStepRequest(threadId, stepRequest);
                            return;
                        }
                        // If the ending step location is filtered, or same as the original location where the step into operation is originated,
                        // do another step of the same kind.
                        if (shouldFilterLocation(thread, context) || shouldDoExtraStepInto(thread)) {
                            StepRequest stepRequest = DebugUtility.stepInto(thread, debugSession.getEventHub(), context.getStepFilters().classNameFilters);
                            setPendingStepRequest(threadId, stepRequest);
                            return;
                        }
                    }
                } catch (IncompatibleThreadStateException | IndexOutOfBoundsException ex) {
                    // ignore.
                }
            }
            context.sendEvent(new Events.StoppedEvent("step", thread.uniqueID()));
        }
    }

    private boolean isStepFiltersConfigured(StepFilters filters) {
        if (filters == null) {
            return false;
        }
        return ArrayUtils.isNotEmpty(filters.classNameFilters) || filters.skipConstructors
               || filters.skipStaticInitializers || filters.skipSynthetics;
    }

    /**
     * Return true if the StepEvent's location is a Method that the user has indicated to filter.
     *
     * @throws IncompatibleThreadStateException
     *                      if the thread is not suspended in the target VM.
     */
    private boolean shouldFilterLocation(ThreadReference thread, IDebugAdapterContext context) throws IncompatibleThreadStateException {
        Location originalLocation = getOriginalStepLocation(thread.uniqueID());
        Location currentLocation = getTopFrame(thread).location();
        if (originalLocation == null || currentLocation == null) {
            return false;
        }
        return !shouldFilterMethod(originalLocation.method(), context) && shouldFilterMethod(currentLocation.method(), context);
    }

    private boolean shouldFilterMethod(Method method, IDebugAdapterContext context) {
        if ((context.getStepFilters().skipStaticInitializers && method.isStaticInitializer())
                || (context.getStepFilters().skipSynthetics && method.isSynthetic())
                || (context.getStepFilters().skipConstructors && method.isConstructor())) {
            return true;
        }
        return false;
    }

    /**
     * Check if the current top stack is same as the original top stack.
     *
     * @throws IncompatibleThreadStateException
     *                      if the thread is not suspended in the target VM.
     */
    private boolean shouldDoExtraStepInto(ThreadReference thread) throws IncompatibleThreadStateException {
        if (getOriginalStackDepth(thread.uniqueID()) != thread.frameCount()) {
            return false;
        }
        Location originalLocation = getOriginalStepLocation(thread.uniqueID());
        if (originalLocation == null) {
            return false;
        }
        Location currentLocation = getTopFrame(thread).location();
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

    /**
     * Return the top stack frame of the target thread.
     *
     * @param thread
     *              the target thread.
     * @return the top frame.
     * @throws IncompatibleThreadStateException
     *                      if the thread is not suspended in the target VM.
     * @throws IndexOutOfBoundsException
     *                      if the thread doesn't contain any stack frame.
     */
    private StackFrame getTopFrame(ThreadReference thread) throws IncompatibleThreadStateException {
        return thread.frame(0);
    }

    private ThreadState getThreadState(long threadId) {
        if (!threadStates.containsKey(threadId)) {
            threadStates.put(threadId, new ThreadState());
        }
        return threadStates.get(threadId);
    }

    private void removeThreadState(long threadId) {
        if (threadStates.containsKey(threadId)) {
            threadStates.remove(threadId);
        }
    }

    private void setPendingStepType(long threadId, Command type) {
        ThreadState state = getThreadState(threadId);
        if (state != null) {
            state.pendingStepType = type;
        }
    }

    private Command getPendingStepType(long threadId) {
        ThreadState state = getThreadState(threadId);
        if (state == null) {
            return Command.UNSUPPORTED;
        }
        return state.pendingStepType;
    }

    private void setPendingStepRequest(long threadId, StepRequest stepRequest) {
        ThreadState state = getThreadState(threadId);
        if (state != null) {
            state.pendingStepRequest = stepRequest;
        }
    }

    private StepRequest getPendingStepRequest(long threadId) {
        ThreadState state = getThreadState(threadId);
        if (state != null) {
            return state.pendingStepRequest;
        }
        return null;
    }

    private void setOriginalStackDepth(long threadId, int depth) {
        ThreadState state = getThreadState(threadId);
        if (state != null) {
            state.stackDepth = depth;
        }
    }

    private int getOriginalStackDepth(long threadId) {
        ThreadState state = getThreadState(threadId);
        if (state == null) {
            return -1;
        }
        return state.stackDepth;
    }

    private void setOriginalStepLocation(long threadId, Location location) {
        ThreadState state = getThreadState(threadId);
        if (state != null) {
            state.stepLocation = location;
        }
    }

    private Location getOriginalStepLocation(long threadId) {
        ThreadState state = getThreadState(threadId);
        if (state != null) {
            return state.stepLocation;
        }
        return null;
    }

    class ThreadState {
        Command pendingStepType;
        StepRequest pendingStepRequest = null;
        int stackDepth = -1;
        Location stepLocation = null;
    }
}
