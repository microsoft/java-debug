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

import com.microsoft.java.debug.core.JdiMethodResult;

public interface IStepResultManager {
    JdiMethodResult setMethodResult(long threadId, JdiMethodResult methodResult);

    JdiMethodResult getMethodResult(long threadId);

    JdiMethodResult removeMethodResult(long threadId);

    void removeAllMethodResults();
}
