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

package com.microsoft.java.debug.core;

import java.util.ArrayList;
import java.util.List;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;

public class DebugSession implements IDebugSession {
    private VirtualMachine vm;
    private EventHub eventHub = new EventHub();

    public DebugSession(VirtualMachine virtualMachine) {
        vm = virtualMachine;
    }

    @Override
    public void start() {
        // request thread events by default
        EventRequest threadStartRequest = vm.eventRequestManager().createThreadStartRequest();
        threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        threadStartRequest.enable();

        EventRequest threadDeathRequest = vm.eventRequestManager().createThreadDeathRequest();
        threadDeathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        threadDeathRequest.enable();

        eventHub.start(vm);
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
            while (!tr.isCollected() && tr.suspendCount() > 1) {
                tr.resume();
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
        if (vm.process() == null || vm.process().isAlive()) {
            vm.exit(0);
        }
    }

    @Override
    public IBreakpoint createBreakpoint(String className, int lineNumber, int hitCount, String condition) {
        return new Breakpoint(vm, this.getEventHub(), className, lineNumber, hitCount, condition);
    }


    @Override
    public void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught) {
        EventRequestManager manager = vm.eventRequestManager();
        ArrayList<ExceptionRequest> legacy = new ArrayList<>(manager.exceptionRequests());
        manager.deleteEventRequests(legacy);
        // When no exception breakpoints are requested, no need to create an empty exception request.
        if (notifyCaught || notifyUncaught) {
            ExceptionRequest request = manager.createExceptionRequest(null, notifyCaught, notifyUncaught);
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            request.enable();
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
}
