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

import com.microsoft.java.debug.core.DebugException;

public interface ISourceLookUpProvider extends IProvider {
    boolean supportsRealtimeBreakpointVerification();

    String[] getFullyQualifiedName(String uri, int[] lines, int[] columns) throws DebugException;

    /**
     * Given a fully qualified class name and source file path, search the associated disk source file.
     *
     * @param fullyQualifiedName
     *                  the fully qualified class name (e.g. com.microsoft.java.debug.core.adapter.ISourceLookUpProvider).
     * @param sourcePath
     *                  the qualified source file path (e.g. com\microsoft\java\debug\core\adapter\ISourceLookupProvider.java).
     * @return the associated source file uri.
     */
    String getSourceFileURI(String fullyQualifiedName, String sourcePath);

    String getSourceContents(String uri);
}
