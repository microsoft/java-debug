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

import java.util.List;
import java.util.Objects;

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
     * Deprecated, please use {@link #getSource(String, String)} instead.
     */
    @Deprecated
    String getSourceFileURI(String fullyQualifiedName, String sourcePath);

    String getSourceContents(String uri);

    /**
     * Retrieves a {@link Source} object representing the source code associated with the given fully qualified class name and source file path.
     * The implementation of this interface can determine a source is "local" or "remote".
     * In case of "remote" a follow up "source" request will be issued by the client
     *
     * @param fullyQualifiedName
     *                  the fully qualified class name,
     *                  e.g., "com.microsoft.java.debug.core.adapter.ISourceLookUpProvider".
     * @param sourcePath
     *                  the qualified source file path,
     *                  e.g., "com/microsoft/java/debug/core/adapter/ISourceLookupProvider.java".
     * @return A {@link Source} object encapsulating the source file URI obtained from
     *                  {@link #getSourceFileURI(String, String)} and the source type as {@link SourceType#LOCAL}.
     */
    default Source getSource(String fullyQualifiedName, String sourcePath) {
        return new Source(getSourceFileURI(fullyQualifiedName, sourcePath), SourceType.LOCAL);
    }

    /**
     * Returns the Java runtime that the specified project's build path used.
     * @param projectName
     *                  the specified project name
     * @return the Java runtime version the specified project used. null if projectName is empty or doesn't exist.
     */
    default String getJavaRuntimeVersion(String projectName) {
        return null;
    }

    /**
     * Return method invocation found in the statement as the given line number of
     * the source file.
     *
     * @param uri The source file where the invocation must be searched.
     * @param line The line number where the invocation must be searched.
     *
     * @return List of found method invocation or empty if not method invocations
     *         can be found.
     */
    List<MethodInvocation> findMethodInvocations(String uri, int line);

    /**
     * Return the line mappings from the original line to the decompiled line.
     *
     * @param uri The uri
     * @return the line mappings from the original line to the decompiled line.
     */
    default int[] getOriginalLineMappings(String uri) {
        return null;
    }

    /**
     * Return the line mappings from the decompiled line to the original line.
     *
     * @param uri The uri
     * @return the line mappings from the decompiled line to the original line.
     */
    default int[] getDecompiledLineMappings(String uri) {
        return null;
    }

    public static class MethodInvocation {
        public String expression;
        public String methodName;
        public String methodSignature;
        public String methodGenericSignature;
        public String declaringTypeName;
        public int lineStart;
        public int lineEnd;
        public int columnStart;
        public int columnEnd;

        @Override
        public int hashCode() {
            return Objects.hash(expression, methodName, methodSignature, methodGenericSignature, declaringTypeName,
                    lineStart, lineEnd, columnStart, columnEnd);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MethodInvocation)) {
                return false;
            }
            MethodInvocation other = (MethodInvocation) obj;
            return Objects.equals(expression, other.expression) && Objects.equals(methodName, other.methodName)
                    && Objects.equals(methodSignature, other.methodSignature)
                    && Objects.equals(methodGenericSignature, other.methodGenericSignature)
                    && Objects.equals(declaringTypeName, other.declaringTypeName) && lineStart == other.lineStart
                    && lineEnd == other.lineEnd && columnStart == other.columnStart && columnEnd == other.columnEnd;
        }
    }
}
