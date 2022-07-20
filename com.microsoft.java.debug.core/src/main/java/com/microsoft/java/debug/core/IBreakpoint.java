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

import java.util.concurrent.CompletableFuture;

public interface IBreakpoint extends IDebugResource {

    String REQUEST_TYPE = "request_type";

    int REQUEST_TYPE_LINE = 0;

    int REQUEST_TYPE_METHOD = 1;

    int REQUEST_TYPE_LAMBDA = 2;

    String className();

    int getLineNumber();

    int getHitCount();

    void setHitCount(int hitCount);

    CompletableFuture<IBreakpoint> install();

    void putProperty(Object key, Object value);

    Object getProperty(Object key);

    String getCondition();

    void setCondition(String condition);

    String getLogMessage();

    void setLogMessage(String logMessage);
}
