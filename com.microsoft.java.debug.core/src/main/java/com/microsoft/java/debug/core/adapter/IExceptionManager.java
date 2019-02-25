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

import com.microsoft.java.debug.core.JdiExceptionReference;

public interface IExceptionManager {
    /**
     * Returns the Exception associated with the thread.
     */
    JdiExceptionReference getException(long threadId);

    /**
     * Removes the Exception associated with the thread. Returns the previous Exception mapping to the thread,
     * null if no mapping exists.
     */
    JdiExceptionReference removeException(long threadId);

    /**
     * Associates an Exception with the thread. Returns the previous Exception mapping to the thread,
     * null if no mapping exists before.
     */
    JdiExceptionReference setException(long threadId, JdiExceptionReference exception);

    /**
     * Clear all Exceptions.
     */
    void removeAllExceptions();
}
