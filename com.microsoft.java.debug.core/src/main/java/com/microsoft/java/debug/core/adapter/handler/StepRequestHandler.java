/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.commons.lang3.ArrayUtils;

import com.microsoft.java.debug.core.AsyncJdwpUtils;
import com.microsoft.java.debug.core.DebugEvent;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.JdiExceptionReference;
import com.microsoft.java.debug.core.JdiMethodResult;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider.MethodInvocation;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.StepArguments;
import com.microsoft.java.debug.core.protocol.Requests.StepFilters;
import com.microsoft.java.debug.core.protocol.Requests.StepInArguments;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VoidValue;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.LocatableEvent;
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

        StepArguments stepArguments = (StepArguments) arguments;
        long threadId = stepArguments.threadId;
        int targetId = (stepArguments instanceof StepInArguments) ? ((StepInArguments) stepArguments).targetId : 0;
        ThreadReference thread = context.getThreadCache().getThread(threadId);
        if (thread == null) {
            thread = DebugUtility.getThread(context.getDebugSession(), threadId);
        }
        if (thread != null) {
            JdiExceptionReference exception = context.getExceptionManager().removeException(threadId);
            context.getStepResultManager().removeMethodResult(threadId);
            try {
                final ThreadReference targetThread = thread;
                ThreadState threadState = new ThreadState();
                threadState.threadId = threadId;
                threadState.pendingStepType = command;
                threadState.eventSubscription = context.getDebugSession().getEventHub().events()
                    .filter(debugEvent -> (debugEvent.event instanceof StepEvent && debugEvent.event.request().equals(threadState.pendingStepRequest))
                        || (debugEvent.event instanceof MethodExitEvent && debugEvent.event.request().equals(threadState.pendingMethodExitRequest))
                        || debugEvent.event instanceof BreakpointEvent
                        || debugEvent.event instanceof ExceptionEvent)
                    .subscribe(debugEvent -> {
                        handleDebugEvent(debugEvent, context.getDebugSession(), context, threadState);
                    });

                if (command == Command.STEPIN) {
                    threadState.pendingStepRequest = DebugUtility.createStepIntoRequest(thread,
                        context.getStepFilters().allowClasses,
                        context.getStepFilters().skipClasses);
                } else if (command == Command.STEPOUT) {
                    threadState.pendingStepRequest = DebugUtility.createStepOutRequest(thread,
                        context.getStepFilters().allowClasses,
                        context.getStepFilters().skipClasses);
                } else {
                    threadState.pendingStepRequest = DebugUtility.createStepOverRequest(thread, null);
                }

                threadState.pendingMethodExitRequest = thread.virtualMachine().eventRequestManager().createMethodExitRequest();
                threadState.pendingMethodExitRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);

                threadState.targetStepIn = targetId > 0
                    ? (MethodInvocation) context.getRecyclableIdPool().getObjectById(targetId) : null;
                if (context.asyncJDWP()) {
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    futures.add(AsyncJdwpUtils.runAsync(() -> {
                        // JDWP Command: TR_FRAMES
                        try {
                            threadState.topFrame = getTopFrame(targetThread);
                            threadState.stepLocation = threadState.topFrame.location();
                            threadState.pendingMethodExitRequest.addClassFilter(threadState.stepLocation.declaringType());
                            if (targetThread.virtualMachine().canUseInstanceFilters()) {
                                try {
                                    // JDWP Command: SF_THIS_OBJECT
                                    ObjectReference thisObject = threadState.topFrame.thisObject();
                                    if (thisObject != null) {
                                        threadState.pendingMethodExitRequest.addInstanceFilter(thisObject);
                                    }
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                        } catch (IncompatibleThreadStateException e1) {
                            throw new CompletionException(e1);
                        }
                    }));
                    futures.add(AsyncJdwpUtils.runAsync(
                        // JDWP Command: OR_IS_COLLECTED
                        () -> threadState.pendingMethodExitRequest.addThreadFilter(targetThread)
                    ));
                    futures.add(AsyncJdwpUtils.runAsync(() -> {
                        try {
                            // JDWP Command: TR_FRAME_COUNT
                            threadState.stackDepth = targetThread.frameCount();
                        } catch (IncompatibleThreadStateException e) {
                            throw new CompletionException(e);
                        }
                    }));
                    futures.add(
                        // JDWP Command: ER_SET
                        AsyncJdwpUtils.runAsync(() -> threadState.pendingStepRequest.enable())
                    );

                    try {
                        AsyncJdwpUtils.await(futures);
                    } catch (CompletionException ex) {
                        if (ex.getCause() instanceof IncompatibleThreadStateException) {
                            throw (IncompatibleThreadStateException) ex.getCause();
                        }
                        throw ex;
                    }

                    // JDWP Command: ER_SET
                    threadState.pendingMethodExitRequest.enable();
                } else {
                    threadState.topFrame = getTopFrame(targetThread);
                    threadState.stackDepth = targetThread.frameCount();
                    threadState.stepLocation = threadState.topFrame.location();
                    threadState.pendingMethodExitRequest.addThreadFilter(thread);
                    threadState.pendingMethodExitRequest.addClassFilter(threadState.stepLocation.declaringType());
                    if (targetThread.virtualMachine().canUseInstanceFilters()) {
                        try {
                            ObjectReference thisObject = threadState.topFrame.thisObject();
                            if (thisObject != null) {
                                threadState.pendingMethodExitRequest.addInstanceFilter(thisObject);
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    threadState.pendingStepRequest.enable();
                    threadState.pendingMethodExitRequest.enable();
                }

                context.getThreadCache().removeEventThread(thread.uniqueID());
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
            } catch (Exception ex) {
                // Roll back the Exception info if stepping fails.
                context.getExceptionManager().setException(threadId, exception);
                final String failureMessage = String.format("Failed to step because of the error '%s'", ex.getMessage());
                throw AdapterUtils.createCompletionException(
                    failureMessage,
                    ErrorCode.STEP_FAILURE,
                    ex.getCause() != null ? ex.getCause() : ex);
            }
        }

        return CompletableFuture.completedFuture(response);
    }

    private void handleDebugEvent(DebugEvent debugEvent, IDebugSession debugSession, IDebugAdapterContext context,
            ThreadState threadState) {
        Event event = debugEvent.event;
        EventRequestManager eventRequestManager = debugSession.getVM().eventRequestManager();

        // When a breakpoint occurs, abort any pending step requests from the same thread.
        if (event instanceof BreakpointEvent || event instanceof ExceptionEvent) {
            // if we have a pending target step in then ignore and continue.
            if (threadState.targetStepIn != null) {
                debugEvent.shouldResume = true;
                return;
            }

            long threadId = ((LocatableEvent) event).thread().uniqueID();
            if (threadId == threadState.threadId && threadState.pendingStepRequest != null) {
                threadState.deleteStepRequest(eventRequestManager);
                threadState.deleteMethodExitRequest(eventRequestManager);
                context.getStepResultManager().removeMethodResult(threadId);
                if (threadState.eventSubscription != null) {
                    threadState.eventSubscription.dispose();
                }
            }
        } else if (event instanceof StepEvent) {
            ThreadReference thread = ((StepEvent) event).thread();
            long threadId = thread.uniqueID();
            threadState.deleteStepRequest(eventRequestManager);
            if (isStepFiltersConfigured(context.getStepFilters()) || threadState.targetStepIn != null) {
                try {
                    if (threadState.pendingStepType == Command.STEPIN || threadState.targetStepIn != null) {
                        int currentStackDepth = thread.frameCount();
                        StackFrame topFrame = getTopFrame(thread);
                        Location currentStepLocation = topFrame.location();
                        if (threadState.targetStepIn != null) {
                            if (isStoppedAtSelectedMethod(topFrame, threadState.targetStepIn)) {
                                // hit: send StoppedEvent
                            } else {
                                if (currentStackDepth > threadState.stackDepth) {
                                    context.getStepResultManager().removeMethodResult(threadId);
                                    threadState.pendingStepRequest = DebugUtility.createStepOutRequest(thread,
                                        context.getStepFilters().allowClasses,
                                        context.getStepFilters().skipClasses);
                                    threadState.pendingStepRequest.enable();
                                    debugEvent.shouldResume = true;
                                    return;
                                } else if (currentStackDepth == threadState.stackDepth) {
                                    // If the ending step location is same as the original location where the step into operation is originated,
                                    // do another step of the same kind.
                                    if (isSameLocation(currentStepLocation, threadState.stepLocation)) {
                                        context.getStepResultManager().removeMethodResult(threadId);
                                        threadState.pendingStepRequest = DebugUtility.createStepIntoRequest(thread,
                                            context.getStepFilters().allowClasses,
                                            context.getStepFilters().skipClasses);
                                        threadState.pendingStepRequest.enable();
                                        debugEvent.shouldResume = true;
                                        return;
                                    }
                                }
                            }
                        } else if (shouldFilterLocation(threadState.stepLocation, currentStepLocation, context)
                                || shouldDoExtraStepInto(threadState.stackDepth, threadState.stepLocation,
                                        currentStackDepth, currentStepLocation)) {
                            // If the ending step location is filtered, or same as the original location where the step into operation is originated,
                            // do another step of the same kind.
                            context.getStepResultManager().removeMethodResult(threadId);
                            String[] allowedClasses = context.getStepFilters().allowClasses;
                            if (currentStackDepth > threadState.stackDepth) {
                                threadState.pendingStepRequest = DebugUtility.createStepOutRequest(thread,
                                    allowedClasses,
                                    context.getStepFilters().skipClasses);
                            } else {
                                threadState.pendingStepRequest = DebugUtility.createStepIntoRequest(thread,
                                        allowedClasses,
                                    context.getStepFilters().skipClasses);
                            }
                            threadState.pendingStepRequest.enable();
                            debugEvent.shouldResume = true;
                            return;
                        }
                    }
                } catch (IncompatibleThreadStateException | IndexOutOfBoundsException ex) {
                    // ignore.
                }
            }
            threadState.deleteMethodExitRequest(eventRequestManager);
            if (threadState.eventSubscription != null) {
                threadState.eventSubscription.dispose();
            }
            context.getThreadCache().addEventThread(thread, "step");
            context.getProtocolServer().sendEvent(new Events.StoppedEvent("step", thread.uniqueID()));
            debugEvent.shouldResume = false;
        } else if (event instanceof MethodExitEvent) {
            MethodExitEvent methodExitEvent = (MethodExitEvent) event;
            long threadId = methodExitEvent.thread().uniqueID();
            if (threadId == threadState.threadId && methodExitEvent.method().equals(threadState.stepLocation.method())) {
                Value returnValue = methodExitEvent.returnValue();
                if (returnValue instanceof VoidValue) {
                    context.getStepResultManager().removeMethodResult(threadId);
                } else {
                    JdiMethodResult methodResult = new JdiMethodResult(methodExitEvent.method(), returnValue);
                    context.getStepResultManager().setMethodResult(threadId, methodResult);
                }
            }
            debugEvent.shouldResume = true;
        }
    }

    private boolean isStoppedAtSelectedMethod(StackFrame frame, MethodInvocation selectedMethod) {
        Method method = frame.location().method();
        if (method != null
            && Objects.equals(method.name(), selectedMethod.methodName)
            && (Objects.equals(method.signature(), selectedMethod.methodSignature)
                || Objects.equals(method.genericSignature(), selectedMethod.methodGenericSignature))) {
            ObjectReference thisObject = frame.thisObject();
            ReferenceType currentType = (thisObject == null) ? method.declaringType() : thisObject.referenceType();
            if ("java.lang.Object".equals(selectedMethod.declaringTypeName)) {
                return true;
            }

            return isSubType(currentType, selectedMethod.declaringTypeName);
        }

        return false;
    }

    private boolean isSubType(ReferenceType currentType, String baseType) {
        if (baseType.equals(currentType.name())) {
            return true;
        }

        if (currentType instanceof ClassType) {
            ClassType classType = (ClassType) currentType;
            ClassType superClassType = classType.superclass();
            if (superClassType != null && isSubType(superClassType, baseType)) {
                return true;
            }

            List<InterfaceType> interfaces = classType.allInterfaces();
            for (InterfaceType iface : interfaces) {
                if (isSubType(iface, baseType)) {
                    return true;
                }
            }
        }

        if (currentType instanceof InterfaceType) {
            List<InterfaceType> superInterfaces = ((InterfaceType) currentType).superinterfaces();
            for (InterfaceType superInterface : superInterfaces) {
                if (isSubType(superInterface, baseType)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isStepFiltersConfigured(StepFilters filters) {
        if (filters == null) {
            return false;
        }
        return ArrayUtils.isNotEmpty(filters.allowClasses) || ArrayUtils.isNotEmpty(filters.skipClasses)
               || ArrayUtils.isNotEmpty(filters.classNameFilters) || filters.skipConstructors
               || filters.skipStaticInitializers || filters.skipSynthetics;
    }

    /**
     * Return true if the StepEvent's location is a Method that the user has indicated to filter.
     *
     * @throws IncompatibleThreadStateException
     *                      if the thread is not suspended in the target VM.
     */
    private boolean shouldFilterLocation(Location originalLocation, Location currentLocation, IDebugAdapterContext context)
            throws IncompatibleThreadStateException {
        if (originalLocation == null || currentLocation == null) {
            return false;
        }
        return !shouldFilterMethod(originalLocation.method(), context) && shouldFilterMethod(currentLocation.method(), context);
    }

    private boolean shouldFilterMethod(Method method, IDebugAdapterContext context) {
        return (context.getStepFilters().skipStaticInitializers && method.isStaticInitializer())
                || (context.getStepFilters().skipSynthetics && method.isSynthetic())
                || (context.getStepFilters().skipConstructors && method.isConstructor());
    }

    /**
     * Check if the current top stack is same as the original top stack and if we
     * are not in target step in we should not request an extra step in. But if we
     * are processing a target step in, we only check if the original and current
     * location are same. If they are not same we request a extra step in.
     *
     * @throws IncompatibleThreadStateException
     *                                          if the thread is not suspended in
     *                                          the target VM.
     */
    private boolean shouldDoExtraStepInto(int originalStackDepth, Location originalLocation, int currentStackDepth,
            Location currentLocation)
            throws IncompatibleThreadStateException {
        if (originalStackDepth != currentStackDepth) {
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
        if (originalLocation.lineNumber() != currentLocation.lineNumber()) {
            return false;
        }

        return true;
    }

    private boolean isSameLocation(Location original, Location current) {
        if (original == null || current == null) {
            return false;
        }

        Method originalMethod = original.method();
        Method currentMethod = current.method();
        return originalMethod.equals(currentMethod)
            && original.lineNumber() == current.lineNumber();
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

    class ThreadState {
        long threadId = -1;
        Command pendingStepType;
        StepRequest pendingStepRequest = null;
        MethodExitRequest pendingMethodExitRequest = null;
        int stackDepth = -1;
        StackFrame topFrame = null;
        Location stepLocation = null;
        Disposable eventSubscription = null;
        MethodInvocation targetStepIn = null;

        public void deleteMethodExitRequest(EventRequestManager manager) {
            DebugUtility.deleteEventRequestSafely(manager, this.pendingMethodExitRequest);
            this.pendingMethodExitRequest = null;
        }

        public void deleteStepRequest(EventRequestManager manager) {
            DebugUtility.deleteEventRequestSafely(manager, this.pendingStepRequest);
            this.pendingStepRequest = null;
        }
    }
}
