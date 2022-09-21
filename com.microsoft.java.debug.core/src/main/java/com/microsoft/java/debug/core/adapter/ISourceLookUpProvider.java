/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
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
import com.microsoft.java.debug.core.JavaBreakpointLocation;
import com.microsoft.java.debug.core.protocol.Types.SourceBreakpoint;

public interface ISourceLookUpProvider extends IProvider {
    boolean supportsRealtimeBreakpointVerification();

    /**
     * Deprecated, please use {@link #getBreakpointLocations(String, SourceBreakpoint[])} instead.
     */
    @Deprecated
    String[] getFullyQualifiedName(String uri, int[] lines, int[] columns) throws DebugException;

    /**
     * Given a set of source breakpoint locations with line and column numbers,
     * verify if they are valid breakpoint locations. If it's a valid location,
     * resolve its enclosing class name, method name and signature (for method
     * breakpoint) and all possible inline breakpoint locations in that line.
     *
     * @param sourceUri
     *                  the source file uri
     * @param sourceBreakpoints
     *                  the source breakpoints with line and column numbers
     * @return Locations of Breakpoints containing context class and method information.
     */
    JavaBreakpointLocation[] getBreakpointLocations(String sourceUri, SourceBreakpoint[] sourceBreakpoints) throws DebugException;

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

    /**
     * Returns the Java runtime that the specified project's build path used.
     * @param projectName
     *                  the specified project name
     * @return the Java runtime version the specified project used. null if projectName is empty or doesn't exist.
     */
    default String getJavaRuntimeVersion(String projectName) {
        return null;
    }
}
