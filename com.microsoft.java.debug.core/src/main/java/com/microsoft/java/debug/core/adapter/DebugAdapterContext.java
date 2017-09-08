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

package com.microsoft.java.debug.core.adapter;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.Events.DebugEvent;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.adapter.variables.VariableFormatterFactory;

public class DebugAdapterContext implements IDebugAdapterContext {
    private static final int MAX_CACHE_ITEMS = 10000;
    private Map<String, String> sourceMappingCache = Collections.synchronizedMap(new LRUCache<>(MAX_CACHE_ITEMS));
    private DebugAdapter debugAdapter;

    private IDebugSession debugSession;
    private boolean debuggerLinesStartAt1 = true;
    private boolean debuggerPathsAreUri = true;
    private boolean clientLinesStartAt1 = true;
    private boolean clientPathsAreUri = false;
    private boolean isAttached = false;
    private String[] sourcePaths;
    private Charset debuggeeEncoding;

    private IdCollection<String> sourceReferences = new IdCollection<>();
    private RecyclableObjectPool<Long, Object> recyclableIdPool = new RecyclableObjectPool<>();
    private IVariableFormatter variableFormatter = VariableFormatterFactory.createVariableFormatter();

    public DebugAdapterContext(DebugAdapter debugAdapter) {
        this.debugAdapter = debugAdapter;
    }

    @Override
    public void sendEvent(DebugEvent event) {
        this.debugAdapter.sendEvent(event);
    }

    @Override
    public void sendEventAsync(DebugEvent event) {
        this.debugAdapter.sendEventLater(event);
    }

    @Override
    public <T extends IProvider> T getProvider(Class<T> clazz) {
        return this.debugAdapter.getProvider(clazz);
    }

    @Override
    public void setDebugSession(IDebugSession session) {
        this.debugSession = session;
    }

    @Override
    public IDebugSession getDebugSession() {
        return this.debugSession;
    }

    @Override
    public boolean isDebuggerLinesStartAt1() {
        return this.debuggerLinesStartAt1;
    }

    @Override
    public void setDebuggerLinesStartAt1(boolean debuggerLinesStartAt1) {
        this.debuggerLinesStartAt1 = debuggerLinesStartAt1;
    }

    @Override
    public boolean isDebuggerPathsAreUri() {
        return this.debuggerPathsAreUri;
    }

    @Override
    public void setDebuggerPathsAreUri(boolean debuggerPathsAreUri) {
        this.debuggerPathsAreUri = debuggerPathsAreUri;
    }

    @Override
    public boolean isClientLinesStartAt1() {
        return this.clientLinesStartAt1;
    }

    @Override
    public void setClientLinesStartAt1(boolean clientLinesStartAt1) {
        this.clientLinesStartAt1 = clientLinesStartAt1;
    }

    @Override
    public boolean isClientPathsAreUri() {
        return this.clientPathsAreUri;
    }

    @Override
    public void setClientPathsAreUri(boolean clientPathsAreUri) {
        this.clientPathsAreUri = clientPathsAreUri;
    }

    @Override
    public boolean isAttached() {
        return this.isAttached;
    }

    @Override
    public void setAttached(boolean attached) {
        this.isAttached = attached;
    }

    public String[] getSourcePaths() {
        return this.sourcePaths;
    }

    public void setSourcePaths(String[] sourcePaths) {
        this.sourcePaths = sourcePaths;
    }

    @Override
    public String getSourceUri(int sourceReference) {
        return this.sourceReferences.get(sourceReference);
    }

    @Override
    public int createSourceReference(String uri) {
        return this.sourceReferences.create(uri);
    }

    @Override
    public RecyclableObjectPool<Long, Object> getRecyclableIdPool() {
        return this.recyclableIdPool;
    }

    @Override
    public void setRecyclableIdPool(RecyclableObjectPool<Long, Object> idPool) {
        this.recyclableIdPool = idPool;
    }

    @Override
    public IVariableFormatter getVariableFormatter() {
        return this.variableFormatter;
    }

    @Override
    public void setVariableFormatter(IVariableFormatter variableFormatter) {
        this.variableFormatter = variableFormatter;
    }

    @Override
    public Map<String, String> getSourceLookupCache() {
        return this.sourceMappingCache;
    }

    @Override
    public void setDebuggeeEncoding(Charset encoding) {
        this.debuggeeEncoding = encoding;
    }

    @Override
    public Charset getDebuggeeEncoding() {
        return this.debuggeeEncoding;
    }
}
