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

package com.microsoft.java.debug.plugin.internal;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

import com.microsoft.java.debug.core.DebugException;

public class ResolveElementHandler {

    /**
     * Resolve the Java element at the selected position.
     * @return the resolved Java element information.
     */
    public static Object resolveElementAtSelection(List<Object> arguments, IProgressMonitor monitor) throws DebugException {
        if (arguments == null || arguments.size() < 3) {
            return Collections.emptyList();
        }

        String uri = (String) arguments.get(0);
        int line = (int) Math.round((double) arguments.get(1));
        int column = (int) Math.round((double) arguments.get(2));
        final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
        try {
            IJavaElement element = JDTUtils.findElementAtSelection(unit, line, column,
                    JavaLanguageServerPlugin.getPreferencesManager(), monitor);
            if (element instanceof IMethod) {
                return new JavaElement(((IMethod) element).getDeclaringType().getFullyQualifiedName(),
                    element.getJavaProject().getProject().getName(),
                    ((IMethod) element).isMainMethod());
            } else if (element instanceof IType) {
                return new JavaElement(((IType) element).getFullyQualifiedName(),
                    element.getJavaProject().getProject().getName(),
                    ResolveMainMethodHandler.getMainMethod((IType) element) != null);
            }
        } catch (JavaModelException e) {
            throw new DebugException("Failed to resolve the selected element information: " + e.getMessage(), e);
        }

        return null;
    }

    static class JavaElement {
        private String declaringType;
        private String projectName;
        private boolean hasMainMethod;

        public JavaElement(String declaringType, String projectName, boolean hasMainMethod) {
            this.declaringType = declaringType;
            this.projectName = projectName;
            this.hasMainMethod = hasMainMethod;
        }
    }
}