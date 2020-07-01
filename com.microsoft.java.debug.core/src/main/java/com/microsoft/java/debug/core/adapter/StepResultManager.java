/*******************************************************************************
* Copyright (c) 2020 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.microsoft.java.debug.core.JdiMethodResult;

public class StepResultManager implements IStepResultManager {
    private Map<Long, JdiMethodResult> methodResults = Collections.synchronizedMap(new HashMap<>());

    @Override
    public JdiMethodResult getMethodResult(long threadId) {
        return this.methodResults.get(threadId);
    }

    @Override
    public JdiMethodResult removeMethodResult(long threadId) {
        return this.methodResults.remove(threadId);
    }

    @Override
    public JdiMethodResult setMethodResult(long threadId, JdiMethodResult methodResult) {
        return this.methodResults.put(threadId, methodResult);
    }
}
