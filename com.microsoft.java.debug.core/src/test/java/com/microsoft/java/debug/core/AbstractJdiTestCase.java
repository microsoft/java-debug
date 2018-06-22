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

import org.easymock.EasyMockSupport;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;

public abstract class AbstractJdiTestCase extends EasyMockSupport {
    private static int TEST_TIME_OUT = 1000 * 10;

    protected static BreakpointEvent staticBreakpointEvent;

    protected void resume() {
        getCurrentDebugSession().resume();
    }

    protected BreakpointEvent waitForBreakPointEvent(String breakpointAtClass, int line) throws Exception {
        if (staticBreakpointEvent != null) {
            return staticBreakpointEvent;
        }
        IDebugSession debugSession = getCurrentDebugSession();

        IBreakpoint breakpointToAdd = debugSession.createBreakpoint(breakpointAtClass, line, 0, null, null);
        breakpointToAdd.install().thenAccept(t -> {
            System.out.println("Breakpoint is accepted.");
        });
        debugSession.start();
        debugSession.getEventHub().breakpointEvents().subscribe(breakpoint -> {
            System.out.println("Breakpoint is hit.");
            breakpoint.shouldResume = false;
            staticBreakpointEvent = (BreakpointEvent) breakpoint.event;
            synchronized (debugSession) {
                debugSession.notifyAll();
            }
        });

        synchronized (debugSession) {
            debugSession.wait(TEST_TIME_OUT);
        }
        return staticBreakpointEvent;

    }

    protected ObjectReference getObjectReference(String className) {
        ReferenceType clz =
            staticBreakpointEvent.virtualMachine().classesByName(className).get(0);
        return clz.instances(1).get(0);
    }

    protected VirtualMachine getVM() {
        return staticBreakpointEvent.virtualMachine();
    }

    protected LocalVariable getLocalVariable(String name) throws AbsentInformationException {
        StackFrame frame = getStackFrame();
        return frame.visibleVariableByName(name);
    }

    protected Value getLocalValue(String name) throws AbsentInformationException {
        StackFrame frame = getStackFrame();
        return frame.getValue(frame.visibleVariableByName(name));

    }


    protected StackFrame getStackFrame() {
        if (this.staticBreakpointEvent == null || !this.staticBreakpointEvent.thread().isAtBreakpoint()) {
            return null;
        }
        try {
            return this.staticBreakpointEvent.thread().frame(0);

        } catch (IncompatibleThreadStateException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected StackFrame getSecondLevelStackFrame() {
        if (this.staticBreakpointEvent == null || !this.staticBreakpointEvent.thread().isAtBreakpoint()) {
            return null;
        }
        try {
            return this.staticBreakpointEvent.thread().frame(1);

        } catch (IncompatibleThreadStateException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected abstract IDebugSession getCurrentDebugSession();
}
