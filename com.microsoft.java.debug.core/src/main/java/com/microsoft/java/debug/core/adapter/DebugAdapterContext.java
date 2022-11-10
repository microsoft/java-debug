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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.DebugSettings.AsyncMode;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.adapter.variables.VariableFormatterFactory;
import com.microsoft.java.debug.core.protocol.IProtocolServer;
import com.microsoft.java.debug.core.protocol.Requests.StepFilters;

import org.apache.commons.lang3.ArrayUtils;

public class DebugAdapterContext implements IDebugAdapterContext {
    private static final int MAX_CACHE_ITEMS = 10000;
    private final StepFilters defaultFilters = new StepFilters();
    private Map<String, String> sourceMappingCache = Collections.synchronizedMap(new LRUCache<>(MAX_CACHE_ITEMS));
    private IProviderContext providerContext;
    private IProtocolServer server;

    private IDebugSession debugSession;
    private boolean debuggerLinesStartAt1 = true;
    // The Java model on debugger uses 0-based column number.
    private boolean debuggerColumnStartAt1 = false;
    private boolean debuggerPathsAreUri = true;
    private boolean clientLinesStartAt1 = true;
    private boolean clientColumnsStartAt1 = true;
    private boolean clientPathsAreUri = false;
    private boolean supportsRunInTerminalRequest;
    private boolean isAttached = false;
    private String[] sourcePaths;
    private Charset debuggeeEncoding;
    private transient boolean vmTerminated;
    private boolean isVmStopOnEntry = false;
    private LaunchMode launchMode = LaunchMode.DEBUG;
    private Process debuggeeProcess;
    private String mainClass;
    private StepFilters stepFilters;
    private Path classpathJar = null;
    private Path argsfile = null;
    private boolean isInitialized = false;

    private long shellProcessId = -1;
    private long processId = -1;

    private boolean localDebugging = true;
    private long jdwpLatency = 0;

    private IdCollection<String> sourceReferences = new IdCollection<>();
    private RecyclableObjectPool<Long, Object> recyclableIdPool = new RecyclableObjectPool<>();
    private IVariableFormatter variableFormatter = VariableFormatterFactory.createVariableFormatter();

    private IStackFrameManager stackFrameManager = new StackFrameManager();
    private IExceptionManager exceptionManager = new ExceptionManager();
    private IBreakpointManager breakpointManager = new BreakpointManager();
    private IStepResultManager stepResultManager = new StepResultManager();
    private ThreadCache threadCache = new ThreadCache();

    public DebugAdapterContext(IProtocolServer server, IProviderContext providerContext) {
        this.providerContext = providerContext;
        this.server = server;
    }

    @Override
    public IProtocolServer getProtocolServer() {
        return server;
    }

    @Override
    public <T extends IProvider> T getProvider(Class<T> clazz) {
        return providerContext.getProvider(clazz);
    }

    @Override
    public void setDebugSession(IDebugSession session) {
        debugSession = session;
    }

    @Override
    public IDebugSession getDebugSession() {
        return debugSession;
    }

    @Override
    public boolean isDebuggerLinesStartAt1() {
        return debuggerLinesStartAt1;
    }

    @Override
    public void setDebuggerLinesStartAt1(boolean debuggerLinesStartAt1) {
        this.debuggerLinesStartAt1 = debuggerLinesStartAt1;
    }

    public boolean isDebuggerColumnsStartAt1() {
        return debuggerColumnStartAt1;
    }

    @Override
    public boolean isDebuggerPathsAreUri() {
        return debuggerPathsAreUri;
    }

    @Override
    public void setDebuggerPathsAreUri(boolean debuggerPathsAreUri) {
        this.debuggerPathsAreUri = debuggerPathsAreUri;
    }

    @Override
    public boolean isClientLinesStartAt1() {
        return clientLinesStartAt1;
    }

    @Override
    public void setClientLinesStartAt1(boolean clientLinesStartAt1) {
        this.clientLinesStartAt1 = clientLinesStartAt1;
    }

    public boolean isClientColumnsStartAt1() {
        return clientColumnsStartAt1;
    }

    public void setClientColumnsStartAt1(boolean clientColumnsStartAt1) {
        this.clientColumnsStartAt1 = clientColumnsStartAt1;
    }

    @Override
    public boolean isClientPathsAreUri() {
        return clientPathsAreUri;
    }

    @Override
    public void setClientPathsAreUri(boolean clientPathsAreUri) {
        this.clientPathsAreUri = clientPathsAreUri;
    }

    @Override
    public void setSupportsRunInTerminalRequest(boolean supportsRunInTerminalRequest) {
        this.supportsRunInTerminalRequest = supportsRunInTerminalRequest;
    }

    @Override
    public boolean supportsRunInTerminalRequest() {
        return supportsRunInTerminalRequest;
    }

    @Override
    public boolean isAttached() {
        return isAttached;
    }

    @Override
    public void setAttached(boolean attached) {
        isAttached = attached;
    }

    @Override
    public String[] getSourcePaths() {
        return sourcePaths;
    }

    @Override
    public void setSourcePaths(String[] sourcePaths) {
        this.sourcePaths = sourcePaths;
    }

    @Override
    public String getSourceUri(int sourceReference) {
        return sourceReferences.get(sourceReference);
    }

    @Override
    public int createSourceReference(String uri) {
        return sourceReferences.create(uri);
    }

    @Override
    public RecyclableObjectPool<Long, Object> getRecyclableIdPool() {
        return recyclableIdPool;
    }

    @Override
    public void setRecyclableIdPool(RecyclableObjectPool<Long, Object> idPool) {
        recyclableIdPool = idPool;
    }

    @Override
    public IVariableFormatter getVariableFormatter() {
        return variableFormatter;
    }

    @Override
    public void setVariableFormatter(IVariableFormatter variableFormatter) {
        this.variableFormatter = variableFormatter;
    }

    @Override
    public Map<String, String> getSourceLookupCache() {
        return sourceMappingCache;
    }

    @Override
    public void setDebuggeeEncoding(Charset encoding) {
        debuggeeEncoding = encoding;
    }

    @Override
    public Charset getDebuggeeEncoding() {
        return debuggeeEncoding;
    }

    @Override
    public void setVmTerminated() {
        vmTerminated = true;
    }

    @Override
    public boolean isVmTerminated() {
        return vmTerminated;
    }

    @Override
    public void setVmStopOnEntry(boolean stopOnEntry) {
        isVmStopOnEntry = stopOnEntry;
    }

    @Override
    public boolean isVmStopOnEntry() {
        return isVmStopOnEntry;
    }

    @Override
    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    @Override
    public String getMainClass() {
        return this.mainClass;
    }

    @Override
    public void setStepFilters(StepFilters stepFilters) {
        // For backward compatibility, merge the classNameFilters to skipClasses.
        if (stepFilters != null && ArrayUtils.isNotEmpty(stepFilters.classNameFilters)) {
            Set<String> patterns = new LinkedHashSet<>();
            if (ArrayUtils.isNotEmpty(stepFilters.skipClasses)) {
                patterns.addAll(Arrays.asList(stepFilters.skipClasses));
            }

            patterns.addAll(Arrays.asList(stepFilters.classNameFilters));
            stepFilters.skipClasses = patterns.toArray(new String[0]);
        }
        this.stepFilters = stepFilters;
    }

    @Override
    public StepFilters getStepFilters() {
        if (stepFilters != null) {
            return stepFilters;
        } else if (DebugSettings.getCurrent().stepFilters != null) {
            return DebugSettings.getCurrent().stepFilters;
        }

        return defaultFilters;
    }

    @Override
    public IStackFrameManager getStackFrameManager() {
        return stackFrameManager;
    }

    @Override
    public LaunchMode getLaunchMode() {
        return launchMode;
    }

    @Override
    public void setLaunchMode(LaunchMode launchMode) {
        this.launchMode = launchMode;
    }

    @Override
    public Process getDebuggeeProcess() {
        return this.debuggeeProcess;
    }

    @Override
    public void setDebuggeeProcess(Process debuggeeProcess) {
        this.debuggeeProcess = debuggeeProcess;
    }

    @Override
    public void setClasspathJar(Path classpathJar) {
        this.classpathJar = classpathJar;
    }

    @Override
    public Path getClasspathJar() {
        return this.classpathJar;
    }

    @Override
    public void setArgsfile(Path argsfile) {
        this.argsfile = argsfile;
    }

    @Override
    public Path getArgsfile() {
        return this.argsfile;
    }

    @Override
    public IExceptionManager getExceptionManager() {
        return this.exceptionManager;
    }

    @Override
    public IBreakpointManager getBreakpointManager() {
        return breakpointManager;
    }

    @Override
    public IStepResultManager getStepResultManager() {
        return stepResultManager;
    }

    @Override
    public long getProcessId() {
        return this.processId;
    }

    @Override
    public long getShellProcessId() {
        return this.shellProcessId;
    }

    @Override
    public void setProcessId(long processId) {
        this.processId = processId;
    }

    @Override
    public void setShellProcessId(long shellProcessId) {
        this.shellProcessId = shellProcessId;
    }

    @Override
    public void setThreadCache(ThreadCache cache) {
        this.threadCache = cache;
    }

    @Override
    public ThreadCache getThreadCache() {
        return this.threadCache;
    }

    @Override
    public boolean asyncJDWP() {
        /**
         * If we take 1 second as the acceptable latency for DAP requests,
         * With a single-threaded strategy for handling JDWP requests,
         * a latency of about 15ms per JDWP request can ensure the responsiveness
         * for most DAPs. It allows sending 66 JDWP requests within 1 seconds,
         * which can cover most DAP operations such as breakpoint, threads,
         * call stack, step and continue.
         */
        return asyncJDWP(15);
    }

    @Override
    public boolean asyncJDWP(long usableLatency) {
        return DebugSettings.getCurrent().asyncJDWP == AsyncMode.ON
            || (DebugSettings.getCurrent().asyncJDWP == AsyncMode.AUTO && this.jdwpLatency > usableLatency);
    }

    public boolean isLocalDebugging() {
        return localDebugging;
    }

    public void setLocalDebugging(boolean local) {
        this.localDebugging = local;
    }

    @Override
    public long getJDWPLatency() {
        return this.jdwpLatency;
    }

    @Override
    public void setJDWPLatency(long baseLatency) {
        this.jdwpLatency = baseLatency;
    }

    @Override
    public boolean isInitialized() {
        return isInitialized;
    }

    @Override
    public void setInitialized(boolean isInitialized) {
        this.isInitialized = isInitialized;
    }
}
