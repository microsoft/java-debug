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

import java.util.List;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

public interface IDebugSession {
    void start();

    void suspend();

    void resume();

    void detach();

    void terminate();

    // breakpoints
    IBreakpoint createBreakpoint(String className, int lineNumber, int hitCount, String condition, String logMessage);

    IBreakpoint createBreakpoint(JavaBreakpointLocation sourceLocation, int hitCount, String condition, String logMessage);

    IWatchpoint createWatchPoint(String className, String fieldName, String accessType, String condition, int hitCount);

    void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught);

    void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught, String[] classFilters, String[] classExclusionFilters);

    void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught, String[] exceptionTypes, String[] classFilters, String[] classExclusionFilters);

    void setExceptionBreakpoints(boolean notifyCaught, boolean notifyUncaught, String[] exceptionTypes, String[] classFilters, String[] classExclusionFilters,
        boolean async);

    IMethodBreakpoint createFunctionBreakpoint(String className, String functionName, String condition, int hitCount);

    Process process();

    List<ThreadReference> getAllThreads();

    IEventHub getEventHub();

    VirtualMachine getVM();

    /**
     * Returns whether breakpoints should suspend all threads or just the event thread.
     * This value is captured at session start and persists for the session lifetime.
     */
    boolean shouldSuspendAllThreads();
}
