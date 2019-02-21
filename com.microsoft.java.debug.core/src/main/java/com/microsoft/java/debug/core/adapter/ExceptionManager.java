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

package com.microsoft.java.debug.core.adapter;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.java.debug.core.JdiException;

public class ExceptionManager implements IExceptionManager {
    private Map<Long, JdiException> exceptions = new HashMap<>();

    @Override
    public synchronized JdiException getException(long threadId) {
        return exceptions.get(threadId);
    }

    @Override
    public synchronized void removeException(long threadId) {
        exceptions.remove(threadId);
    }

    @Override
    public synchronized void addException(long threadId, JdiException exception) {
        exceptions.put(threadId, exception);
    }
}
