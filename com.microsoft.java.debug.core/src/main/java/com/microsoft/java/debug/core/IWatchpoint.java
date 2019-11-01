/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core;

import java.util.concurrent.CompletableFuture;

public interface IWatchpoint extends IDebugResource {
    String className();

    String fieldName();

    String accessType();

    CompletableFuture<IWatchpoint> install();

    void putProperty(Object key, Object value);

    Object getProperty(Object key);

    int getHitCount();

    void setHitCount(int hitCount);

    String getCondition();

    void setCondition(String condition);
}
