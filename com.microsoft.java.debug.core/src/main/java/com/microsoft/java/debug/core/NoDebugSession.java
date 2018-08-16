/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core;

import java.util.List;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

public class NoDebugSession implements IDebugSession {
    private Process process = null;

    public NoDebugSession(Process process) {
        this.process = process;
    }

    @Override
    public void start() {
    }

    @Override
    public void suspend() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void detach() {
    }

    @Override
    public void terminate() {
        if (process != null) {
            this.process.destroy();
        }
    }

    @Override
    public IBreakpoint createBreakpoint(String className, int lineNumber, int hitCount, String condition,
            String logMessage) {
        return null;
    }

    @Override
    public void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught) {
    }

    @Override
    public Process process() {
        return this.process;
    }

    @Override
    public List<ThreadReference> getAllThreads() {
        return null;
    }

    @Override
    public IEventHub getEventHub() {
        return null;
    }

    @Override
    public VirtualMachine getVM() {
        return null;
    }

}
