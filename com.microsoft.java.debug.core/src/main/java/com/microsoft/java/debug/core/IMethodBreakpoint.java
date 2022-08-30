/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Gayan Perera - initial API and implementation
*******************************************************************************/
package com.microsoft.java.debug.core;

import java.util.concurrent.CompletableFuture;

public interface IMethodBreakpoint extends IDebugResource {
    String methodName();

    String className();

    int getHitCount();

    String getCondition();

    void setHitCount(int hitCount);

    void setCondition(String condition);

    CompletableFuture<IMethodBreakpoint> install();

    Object getProperty(Object key);

    void putProperty(Object key, Object value);

    default void setAsync(boolean async) {
    }

    default boolean async() {
        return false;
    }
}
