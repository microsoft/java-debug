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

import com.sun.jdi.ObjectReference;

public class JdiExceptionReference {
    public ObjectReference exception;
    public boolean isUncaught;

    public JdiExceptionReference(ObjectReference exception, boolean isUncaught) {
        this.exception = exception;
        this.isUncaught = isUncaught;
    }
}
