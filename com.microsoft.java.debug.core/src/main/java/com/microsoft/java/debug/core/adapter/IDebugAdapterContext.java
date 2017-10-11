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
import java.util.Map;

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;

public interface IDebugAdapterContext {
    /**
     * Send debug event synchronously.
     *
     * @param event
     *            the debug event
     */
    void sendEvent(Events.DebugEvent event);

    /**
     * Send debug event asynchronously.
     *
     * @param event
     *            the debug event
     */
    void sendEventAsync(Events.DebugEvent event);

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

    boolean isClientPathsAreUri();

    void setClientPathsAreUri(boolean clientPathsAreUri);

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
}
