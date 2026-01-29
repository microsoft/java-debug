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

package com.microsoft.java.debug.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;

import io.reactivex.disposables.Disposable;

public class DebugSession implements IDebugSession {
    private VirtualMachine vm;
    private EventHub eventHub = new EventHub();
    private List<EventRequest> eventRequests = new ArrayList<>();
    private List<Disposable> subscriptions = new ArrayList<>();
    private final boolean suspendAllThreads;

    public DebugSession(VirtualMachine virtualMachine) {
        vm = virtualMachine;
        // Capture suspend policy at session start - this persists for the session lifetime
        this.suspendAllThreads = DebugSettings.getCurrent().suspendAllThreads;
    }

    @Override
    public void start() {
        boolean supportsVirtualThreads = mayCreateVirtualThreads();

        // request thread events by default
        EventRequest threadStartRequest = vm.eventRequestManager().createThreadStartRequest();
        threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        if (supportsVirtualThreads) {
            addPlatformThreadsOnlyFilter(threadStartRequest);
        }
        threadStartRequest.enable();

        EventRequest threadDeathRequest = vm.eventRequestManager().createThreadDeathRequest();
        threadDeathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        if (supportsVirtualThreads) {
            addPlatformThreadsOnlyFilter(threadDeathRequest);
        }
        threadDeathRequest.enable();

        eventHub.start(vm);
    }

    private boolean mayCreateVirtualThreads() {
        try {
            Method method = vm.getClass().getMethod("mayCreateVirtualThreads");
            return (boolean) method.invoke(vm);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // ignore
        }

        return false;
    }

    /**
     * For thread start and thread death events, restrict the events so they are only sent for platform threads.
     */
    private void addPlatformThreadsOnlyFilter(EventRequest threadLifecycleRequest) {
        try {
            Method method = threadLifecycleRequest.getClass().getMethod("addPlatformThreadsOnlyFilter");
            method.invoke(threadLifecycleRequest);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // ignore
        }
    }

    @Override
    public void suspend() {
        vm.suspend();
    }

    @Override
    public void resume() {
        /**
         * To ensure that all threads are fully resumed when the VM is resumed, make sure the suspend count
         * of each thread is no larger than 1.
         * Notes: Decrementing the thread' suspend count to 1 is on purpose, because it doesn't break the
         * the thread's suspend state, and also make sure the next instruction vm.resume() is able to resume
         * all threads fully.
         */
        for (ThreadReference tr : DebugUtility.getAllThreadsSafely(this)) {
            try {
                while (tr.suspendCount() > 1) {
                    tr.resume();
                }
            } catch (ObjectCollectedException ex) {
                // Skip it if the thread is garbage collected.
            }
        }
        vm.resume();
    }

    @Override
    public void detach() {
        vm.dispose();
    }

    @Override
    public void terminate() {
        if (vm.process() != null && vm.process().isAlive()) {
            vm.process().destroy();
        } else if (vm.process() == null || vm.process().isAlive()) {
            vm.exit(0);
        }
    }

    @Override
    public IBreakpoint createBreakpoint(JavaBreakpointLocation sourceLocation, int hitCount, String condition, String logMessage) {
        return new EvaluatableBreakpoint(vm, this.getEventHub(), sourceLocation, hitCount, condition, logMessage, suspendAllThreads);
    }

    @Override
    public IBreakpoint createBreakpoint(String className, int lineNumber, int hitCount, String condition, String logMessage) {
        return new EvaluatableBreakpoint(vm, this.getEventHub(), className, lineNumber, hitCount, condition, logMessage, suspendAllThreads);
    }

    @Override
    public IWatchpoint createWatchPoint(String className, String fieldName, String accessType, String condition, int hitCount) {
        return new Watchpoint(vm, this.getEventHub(), className, fieldName, accessType, condition, hitCount, suspendAllThreads);
    }

    @Override
    public void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught) {
        setExceptionBreakpoints(notifyCaught, notifyUncaught, null, null);
    }

    @Override
    public void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught, String[] classFilters, String[] classExclusionFilters) {
        setExceptionBreakpoints(notifyCaught, notifyUncaught, null, classFilters, classExclusionFilters);
    }

    @Override
    public void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught, String[] exceptionTypes,
        String[] classFilters, String[] classExclusionFilters) {
        EventRequestManager manager = vm.eventRequestManager();

        try {
            ArrayList<ExceptionRequest> legacy = new ArrayList<>(manager.exceptionRequests());
            manager.deleteEventRequests(legacy);
            manager.deleteEventRequests(eventRequests);
        } catch (VMDisconnectedException ex) {
            // ignore since removing breakpoints is meaningless when JVM is terminated.
        }
        subscriptions.forEach(subscription -> {
            subscription.dispose();
        });
        subscriptions.clear();
        eventRequests.clear();

        // When no exception breakpoints are requested, no need to create an empty exception request.
        if (notifyCaught || notifyUncaught) {
            // from: https://www.javatips.net/api/REPLmode-master/src/jm/mode/replmode/REPLRunner.java
            // Calling this seems to set something internally to make the
            // Eclipse JDI wake up. Without it, an ObjectCollectedException
            // is thrown on request.enable(). No idea why this works,
            // but at least exception handling has returned. (Suspect that it may
            // block until all or at least some threads are available, meaning
            // that the app has launched and we have legit objects to talk to).
            vm.allThreads();
            // The bug may not have been noticed because the test suite waits for
            // a thread to be available, and queries it by calling allThreads().
            // See org.eclipse.debug.jdi.tests.AbstractJDITest for the example.

            if (exceptionTypes == null || exceptionTypes.length == 0) {
                ExceptionRequest request = manager.createExceptionRequest(null, notifyCaught, notifyUncaught);
                request.setSuspendPolicy(suspendAllThreads ? EventRequest.SUSPEND_ALL : EventRequest.SUSPEND_EVENT_THREAD);
                if (classFilters != null) {
                    for (String classFilter : classFilters) {
                        request.addClassFilter(classFilter);
                    }
                }
                if (classExclusionFilters != null) {
                    for (String exclusionFilter : classExclusionFilters) {
                        request.addClassExclusionFilter(exclusionFilter);
                    }
                }
                request.enable();
                return;
            }

            for (String exceptionType : exceptionTypes) {
                if (StringUtils.isBlank(exceptionType)) {
                    continue;
                }

                // register exception breakpoint in the future loaded classes.
                ClassPrepareRequest classPrepareRequest = manager.createClassPrepareRequest();
                classPrepareRequest.addClassFilter(exceptionType);
                classPrepareRequest.enable();
                eventRequests.add(classPrepareRequest);

                Disposable subscription = eventHub.events()
                    .filter(debugEvent -> debugEvent.event instanceof ClassPrepareEvent
                        && eventRequests.contains(debugEvent.event.request()))
                    .subscribe(debugEvent -> {
                        ClassPrepareEvent event = (ClassPrepareEvent) debugEvent.event;
                        createExceptionBreakpoint(event.referenceType(), notifyCaught, notifyUncaught, classFilters, classExclusionFilters);
                    });
                subscriptions.add(subscription);

                // register exception breakpoint in the loaded classes.
                for (ReferenceType refType : vm.classesByName(exceptionType)) {
                    createExceptionBreakpoint(refType, notifyCaught, notifyUncaught, classFilters, classExclusionFilters);
                }
            }
        }
    }

    @Override
    public void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught, String[] exceptionTypes,
            String[] classFilters, String[] classExclusionFilters, boolean async) {
        if (async) {
            AsyncJdwpUtils.runAsync(() -> {
                setExceptionBreakpoints(notifyCaught, notifyUncaught, exceptionTypes, classFilters, classExclusionFilters);
            });
        } else {
            setExceptionBreakpoints(notifyCaught, notifyUncaught, exceptionTypes, classFilters, classExclusionFilters);
        }
    }

    @Override
    public Process process() {
        return vm.process();
    }

    @Override
    public List<ThreadReference> getAllThreads() {
        return vm.allThreads();
    }

    @Override
    public IEventHub getEventHub() {
        return eventHub;
    }

    @Override
    public VirtualMachine getVM() {
        return vm;
    }

    @Override
    public boolean shouldSuspendAllThreads() {
        return suspendAllThreads;
    }

    @Override
    public IMethodBreakpoint createFunctionBreakpoint(String className, String functionName, String condition,
            int hitCount) {
        return new MethodBreakpoint(vm, this.getEventHub(), className, functionName, condition, hitCount, suspendAllThreads);
    }

    private void createExceptionBreakpoint(ReferenceType refType, boolean notifyCaught, boolean notifyUncaught,
            String[] classFilters, String[] classExclusionFilters) {
        EventRequestManager manager = vm.eventRequestManager();
        ExceptionRequest request = manager.createExceptionRequest(refType, notifyCaught, notifyUncaught);
        request.setSuspendPolicy(suspendAllThreads ? EventRequest.SUSPEND_ALL : EventRequest.SUSPEND_EVENT_THREAD);
        if (classFilters != null) {
            for (String classFilter : classFilters) {
                request.addClassFilter(classFilter);
            }
        }
        if (classExclusionFilters != null) {
            for (String exclusionFilter : classExclusionFilters) {
                request.addClassExclusionFilter(exclusionFilter);
            }
        }
        request.enable();
    }
}
