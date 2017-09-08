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

    String getSourceFileURI(String fullyQualifiedName, String sourcePath);

    String getSourceContents(String uri);
}
