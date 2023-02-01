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

package com.microsoft.java.debug.core.adapter;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.protocol.IProtocolServer;
import com.microsoft.java.debug.core.protocol.Requests.StepFilters;

public interface IDebugAdapterContext {
    IProtocolServer getProtocolServer();

    <T extends IProvider> T getProvider(Class<T> clazz);

    /**
     * Set the debug session.
     * @param session
     *              the new debug session
     */
    void setDebugSession(IDebugSession session);

    /**
     * Get the debug session.
     *
     * @return the debug session.
     */
    IDebugSession getDebugSession();

    boolean isDebuggerLinesStartAt1();

    void setDebuggerLinesStartAt1(boolean debuggerLinesStartAt1);

    boolean isDebuggerPathsAreUri();

    void setDebuggerPathsAreUri(boolean debuggerPathsAreUri);

    boolean isClientLinesStartAt1();

    void setClientLinesStartAt1(boolean clientLinesStartAt1);

    boolean isClientColumnsStartAt1();

    void setClientColumnsStartAt1(boolean clientColumnsStartAt1);

    boolean isDebuggerColumnsStartAt1();

    boolean isClientPathsAreUri();

    void setClientPathsAreUri(boolean clientPathsAreUri);

    void setSupportsRunInTerminalRequest(boolean supportsRunInTerminalRequest);

    boolean supportsRunInTerminalRequest();

    boolean isAttached();

    void setAttached(boolean attached);

    String[] getSourcePaths();

    void setSourcePaths(String[] sourcePaths);

    String getSourceUri(int sourceReference);

    int createSourceReference(String uri);

    RecyclableObjectPool<Long, Object> getRecyclableIdPool();

    void setRecyclableIdPool(RecyclableObjectPool<Long, Object> idPool);

    IVariableFormatter getVariableFormatter();

    void setVariableFormatter(IVariableFormatter variableFormatter);

    Map<String, String> getSourceLookupCache();

    void setDebuggeeEncoding(Charset encoding);

    Charset getDebuggeeEncoding();

    void setVmTerminated();

    boolean isVmTerminated();

    void setVmStopOnEntry(boolean stopOnEntry);

    boolean isVmStopOnEntry();

    void setMainClass(String mainClass);

    String getMainClass();

    void setStepFilters(StepFilters stepFilters);

    StepFilters getStepFilters();

    IStackFrameManager getStackFrameManager();

    LaunchMode getLaunchMode();

    void setLaunchMode(LaunchMode launchMode);

    Process getDebuggeeProcess();

    void setDebuggeeProcess(Process debuggeeProcess);

    void setClasspathJar(Path classpathJar);

    Path getClasspathJar();

    void setArgsfile(Path argsfile);

    Path getArgsfile();

    IExceptionManager getExceptionManager();

    IBreakpointManager getBreakpointManager();

    IStepResultManager getStepResultManager();

    void setShellProcessId(long shellProcessId);

    long getShellProcessId();

    void setProcessId(long processId);

    long getProcessId();

    void setThreadCache(ThreadCache cache);

    ThreadCache getThreadCache();

    boolean asyncJDWP();

    boolean asyncJDWP(long usableLatency/**ms*/);

    boolean isLocalDebugging();

    void setLocalDebugging(boolean local);

    long getJDWPLatency();

    void setJDWPLatency(long baseLatency);

    boolean isInitialized();

    void setInitialized(boolean isInitialized);
}
