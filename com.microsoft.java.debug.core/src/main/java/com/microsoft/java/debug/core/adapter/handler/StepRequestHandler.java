/*******************************************************************************
 * Copyright (c) 2017-2020 Microsoft Corporation and others.
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

import com.microsoft.java.debug.core.DebugEvent;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.JdiExceptionReference;
import com.microsoft.java.debug.core.JdiMethodResult;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IStepFilterProvider;
import com.microsoft.java.debug.core.adapter.StepRequestManager;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.StepArguments;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VoidValue;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;

import io.reactivex.disposables.Disposable;

public class StepRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.STEPIN, Command.STEPOUT, Command.NEXT);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Debug Session doesn't exist.");
        }

        long threadId = ((StepArguments) arguments).threadId;
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), threadId);
        if (thread != null) {
            JdiExceptionReference exception = context.getExceptionManager().removeException(threadId);
            context.getStepRequestManager().removeMethodResult(threadId);
            try {
                StepRequestManager stepRequestManager = context.getStepRequestManager();
                StepRequestManager.ThreadState threadState = stepRequestManager.setThreadState(command, thread);
                Disposable eventSubscription = context.getDebugSession().getEventHub().events()
                    .filter(debugEvent -> (debugEvent.event instanceof StepEvent && debugEvent.event.request().equals(threadState.getPendingStepRequest()))
                        || (debugEvent.event instanceof MethodExitEvent && debugEvent.event.request().equals(threadState.getPendingMethodExitRequest())))
                    .subscribe(debugEvent -> handleDebugEvent(debugEvent, context.getDebugSession(), context, threadState));
                threadState.setEventSubscription(eventSubscription);

                StepRequest stepRequest;
                if (command == Command.STEPIN) {
                    stepRequest = DebugUtility.createStepIntoRequest(thread,
                            context.getStepFilters().allowClasses,
                            context.getStepFilters().skipClasses);
                } else if (command == Command.STEPOUT) {
                    stepRequest = DebugUtility.createStepOutRequest(thread,
                        context.getStepFilters().allowClasses,
                        context.getStepFilters().skipClasses);
                } else {
                    stepRequest = DebugUtility.createStepOverRequest(thread, null);
                }
                threadState.setPendingStepRequest(stepRequest);
                stepRequest.enable();

                MethodExitRequest methodExitRequest = thread.virtualMachine().eventRequestManager().createMethodExitRequest();
                methodExitRequest.addThreadFilter(thread);
                methodExitRequest.addClassFilter(threadState.getStepLocation().declaringType());
                if (thread.virtualMachine().canUseInstanceFilters()) {
                    try {
                        ObjectReference thisObject = getTopFrame(thread).thisObject();
                        if (thisObject != null) {
                            methodExitRequest.addInstanceFilter(thisObject);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                methodExitRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                threadState.setPendingMethodExitRequest(methodExitRequest);
                methodExitRequest.enable();

                DebugUtility.resumeThread(thread);

                ThreadsRequestHandler.checkThreadRunningAndRecycleIds(thread, context);
            } catch (IncompatibleThreadStateException ex) {
                // Roll back the Exception info if stepping fails.
                context.getExceptionManager().setException(threadId, exception);
                final String failureMessage = String.format("Failed to step because the thread '%s' is not suspended in the target VM.", thread.name());
                throw AdapterUtils.createCompletionException(
                    failureMessage,
                    ErrorCode.STEP_FAILURE,
                    ex);
            } catch (IndexOutOfBoundsException ex) {
                // Roll back the Exception info if stepping fails.
                context.getExceptionManager().setException(threadId, exception);
                final String failureMessage = String.format("Failed to step because the thread '%s' doesn't contain any stack frame", thread.name());
                throw AdapterUtils.createCompletionException(
                    failureMessage,
                    ErrorCode.STEP_FAILURE,
                    ex);
            }
        }

        return CompletableFuture.completedFuture(response);
    }

    private void handleDebugEvent(DebugEvent debugEvent, IDebugSession debugSession, IDebugAdapterContext context,
            StepRequestManager.ThreadState threadState) {
        Event event = debugEvent.event;
        EventRequestManager eventRequestManager = debugSession.getVM().eventRequestManager();

        if (event instanceof StepEvent) {
            ThreadReference thread = ((StepEvent) event).thread();
            threadState.deleteStepRequest(eventRequestManager);
            IStepFilterProvider stepFilter = context.getProvider(IStepFilterProvider.class);
            try {
                Location originalLocation = threadState.getStepLocation();
                Location currentLocation = getTopFrame(thread).location();
                Location upperLocation = null;
                if (thread.frameCount() > 1) {
                    upperLocation = thread.frame(1).location();
                }
                if (originalLocation != null && currentLocation != null) {
                    Requests.StepFilters stepFilters = context.getStepFilters();
                    // If we stepped into a method that should be stepped out
                    if (shouldSkipOut(stepFilter, threadState.getStackDepth(), thread.frameCount(), upperLocation, currentLocation)) {
                        doExtraStepOut(debugEvent, thread, stepFilters, threadState);
                        return;
                    }
                    // If the ending location is the same as the original location do another step into.
                    if (shouldDoExtraStep(threadState, originalLocation, thread.frameCount(), currentLocation)) {
                        doExtraStepInto(debugEvent, thread, stepFilters, threadState);
                        return;
                    }
                    // If the ending location should be stepped into
                    if (shouldSkipOver(stepFilter, originalLocation, currentLocation, stepFilters)) {
                        doExtraStepInto(debugEvent, thread, stepFilters, threadState);
                        return;
                    }
                }

            } catch (IncompatibleThreadStateException | IndexOutOfBoundsException ex) {
                // ignore.
            }
            threadState.deletePendingStep(eventRequestManager);
            context.getProtocolServer().sendEvent(new Events.StoppedEvent("step", threadState.getThreadId()));
            debugEvent.shouldResume = false;
        } else if (event instanceof MethodExitEvent) {
            MethodExitEvent methodExitEvent = (MethodExitEvent) event;
            long threadId = methodExitEvent.thread().uniqueID();
            if (threadId == threadState.getThreadId() && methodExitEvent.method().equals(threadState.getStepLocation().method())) {
                Value returnValue = methodExitEvent.returnValue();
                if (returnValue instanceof VoidValue) {
                    context.getStepRequestManager().removeMethodResult(threadId);
                } else {
                    JdiMethodResult methodResult = new JdiMethodResult(methodExitEvent.method(), returnValue);
                    context.getStepRequestManager().setMethodResult(threadId, methodResult);
                }
            }
            debugEvent.shouldResume = true;
        }
    }

    /**
     * Return true if the StepEvent's location is a Method that the user has indicated to step into.
     *
     * @throws IncompatibleThreadStateException
     *                      if the thread is not suspended in the target VM.
     */
    private boolean shouldSkipOver(IStepFilterProvider stepFilter, Location originalLocation, Location currentLocation, Requests.StepFilters stepFilters)
            throws IncompatibleThreadStateException {
        return !stepFilter.shouldSkipOver(originalLocation.method(), stepFilters)
                && stepFilter.shouldSkipOver(currentLocation.method(), stepFilters);
    }

    private boolean shouldSkipOut(IStepFilterProvider stepFilter, int originalStackDepth, int currentStackDepth, Location upperLocation,
                                  Location currentLocation)
            throws IncompatibleThreadStateException {
        if (upperLocation == null) {
            return false;
        }
        if (currentStackDepth <= originalStackDepth) {
            return false;
        }
        return stepFilter.shouldSkipOut(upperLocation, currentLocation.method());
    }

    /**
     * Check if the current top stack is same as the original top stack.
     *
     * @throws IncompatibleThreadStateException
     *                      if the thread is not suspended in the target VM.
     */
    private boolean shouldDoExtraStep(StepRequestManager.ThreadState threadState, Location originalLocation, int currentStackDepth, Location currentLocation)
            throws IncompatibleThreadStateException {
        if (threadState.getStepType() != Command.STEPIN) {
            return false;
        }
        if (threadState.getStackDepth() != currentStackDepth) {
            return false;
        }
        if (originalLocation == null) {
            return false;
        }
        Method originalMethod = originalLocation.method();
        Method currentMethod = currentLocation.method();
        if (!originalMethod.equals(currentMethod)) {
            return false;
        }
        return originalLocation.lineNumber() == currentLocation.lineNumber();
    }

    private void doExtraStepInto(DebugEvent debugEvent, ThreadReference thread, Requests.StepFilters stepFilters, StepRequestManager.ThreadState threadState) {
        StepRequest stepRequest = DebugUtility.createStepIntoRequest(thread, stepFilters.allowClasses, stepFilters.skipClasses);
        threadState.setPendingStepRequest(stepRequest);
        stepRequest.enable();
        debugEvent.shouldResume = true;
    }

    private void doExtraStepOut(DebugEvent debugEvent, ThreadReference thread, Requests.StepFilters stepFilters, StepRequestManager.ThreadState threadState) {
        StepRequest stepRequest = DebugUtility.createStepOutRequest(thread, stepFilters.allowClasses, stepFilters.skipClasses);
        threadState.setPendingStepRequest(stepRequest);
        stepRequest.enable();
        debugEvent.shouldResume = true;
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
}
