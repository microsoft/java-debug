/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.Range;

import com.microsoft.java.debug.core.DebugException;

public class ResolveMainMethodHandler {
    /**
     * Resolve the main methods from the current file.
     * @return an array of main methods.
     */
    public static Object resolveMainMethods(List<Object> arguments) throws Exception {
        if (arguments == null || arguments.size() == 0) {
            return Collections.emptyList();
        }

        String uri = (String) arguments.get(0);
        final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
        if (unit == null || unit.getResource() == null || !unit.getResource().exists()) {
            return Collections.emptyList();
        }

        try {
            return resolveMainMethodCore(unit);
        } catch (Exception e) {
            throw new DebugException("Failed to resolve main method codeLens: " + e.getMessage(), e);
        }
    }

    private static List<MainMethod> resolveMainMethodCore(ICompilationUnit compilationUnit) throws JavaModelException {
        List<MainMethod> codeLenses = new ArrayList<>();
        for (IMethod method : searchMainMethods(compilationUnit)) {
            MainMethod codeLens = constructMainMethodCodeLens(compilationUnit, method);
            if (codeLens != null) {
                codeLenses.add(codeLens);
            }
        }

        return codeLenses;
    }

    private static List<IMethod> searchMainMethods(ICompilationUnit compilationUnit) throws JavaModelException {
        List<IType> potentialTypes = getPotentialMainClassTypes(compilationUnit.getTypes(), 1);
        List<IMethod> result = new ArrayList<>();
        for (IType type: potentialTypes) {
            result.addAll(searchMainMethodsInType(type));
        }

        return result;
    }

    private static List<IMethod> searchMainMethodsInType(IType type) throws JavaModelException {
        for (IMethod method : type.getMethods()) {
            // Have at most one main method in the member methods of the type.
            if (method.isMainMethod()) {
                return Arrays.asList(method);
            }
        }

        return Collections.emptyList();
    }

    private static List<IType> getPotentialMainClassTypes(IType[] types, int level) throws JavaModelException {
        if (types.length == 0) {
            return Collections.emptyList();
        }

        List<IType> result = new ArrayList<>();
        for (IType type: types) {
            result.addAll(getPotentialMainClassTypes(type, level));
        }

        return result;
    }

    private static List<IType> getPotentialMainClassTypes(IType type, int level) throws JavaModelException {
        if (!allowHavingMainMethod(type, level)) {
            return Collections.emptyList();
        }

        List<IType> result = new ArrayList<>();
        result.add(type);
        result.addAll(getPotentialMainClassTypes(type.getTypes(), level + 1));
        return result;
    }

    private static boolean allowHavingMainMethod(IType type, int level) throws JavaModelException {
        // main method can only exist in the static class or top level class.
        return type.isClass() && (level <= 1 || Flags.isStatic(type.getFlags()));
    }

    private static MainMethod constructMainMethodCodeLens(ICompilationUnit typeRoot, IMethod method) throws JavaModelException {
        final Range range = getRange(typeRoot, method);
        IResource resource = typeRoot.getResource();
        if (resource != null) {
            IProject project = resource.getProject();
            if (project != null) {
                String mainClass = method.getDeclaringType().getFullyQualifiedName();
                IJavaProject javaProject = JdtUtils.getJavaProject(project);
                if (javaProject != null) {
                    String moduleName = JdtUtils.getModuleName(javaProject);
                    if (moduleName != null) {
                        mainClass = moduleName + "/" + mainClass;
                    }
                }

                String projectName = ProjectsManager.DEFAULT_PROJECT_NAME.equals(project.getName()) ? null : project.getName();
                return new MainMethod(range, mainClass, projectName);
            }
        }

        return null;
    }

    private static Range getRange(ICompilationUnit typeRoot, IJavaElement element) throws JavaModelException {
        ISourceRange r = ((ISourceReference) element).getNameRange();
        final Range range = JDTUtils.toRange(typeRoot, r.getOffset(), r.getLength());
        return range;
    }

    static class MainMethod {
        private Range range;
        private String mainClass;
        private String projectName;

        public MainMethod(Range range, String mainClass, String projectName) {
            this.range = range;
            this.mainClass = mainClass;
            this.projectName = projectName;
        }
    }
}
