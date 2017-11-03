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

import java.util.Map;

import com.microsoft.java.debug.core.IDebugSession;

public interface IProvider {
    /**
     * Initialize this provider.
     * @param debugSession
     *            The associated debug session
     * @param options
     *            the options
     */
    default void initialize(IDebugSession debugSession, Map<String, Object> options) {
    }
}
