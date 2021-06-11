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

public interface IProvider {
    /**
     * Initialize this provider.
     *
     * @param debugContext
     *            The associated debug context
     * @param options
     *            the options
     */
    default void initialize(IDebugAdapterContext debugContext, Map<String, Object> options) {
    }

    /**
     * Close the provider and free all associated resources.
     */
    default void close() {
    }
}
