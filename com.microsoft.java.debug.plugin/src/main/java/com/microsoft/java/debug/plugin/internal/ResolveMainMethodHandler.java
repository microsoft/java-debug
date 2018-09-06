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
            throw new Exception("Failed to resolve main method codeLens: " + e.getMessage(), e);
        }
    }

    private static List<MainMethod> resolveMainMethodCore(ICompilationUnit typeRoot) throws JavaModelException {
        List<MainMethod> mainMethods = new ArrayList<>();

        List<IMethod> jdtMainMethods = new ArrayList<>();
        IType[] topLevelTypes = typeRoot.getTypes();
        for (IType topType : topLevelTypes) {
            jdtMainMethods.addAll(resolveMainMethodCore(topType, 1));
        }

        for (IMethod method : jdtMainMethods) {
            MainMethod mainMethod = constructMainMethod(typeRoot, method);
            if (mainMethod != null) {
                mainMethods.add(mainMethod);
            }
        }

        return mainMethods;
    }

    private static List<IMethod> resolveMainMethodCore(IType type, int level) throws JavaModelException {
        // main method can only exist in the static class or top level class.
        if (level > 1 && !Flags.isStatic(type.getFlags())) {
            return Collections.emptyList();
        }

        List<IMethod> mainMethods = new ArrayList<>();

        // Have at most one main method at the same level.
        for (IMethod method : type.getMethods()) {
            if (method.isMainMethod()) {
                mainMethods.add(method);
                break;
            }
        }

        for (IType child : type.getTypes()) {
            mainMethods.addAll(resolveMainMethodCore(child, level + 1));
        }

        return mainMethods;
    }

    private static Range getRange(ICompilationUnit typeRoot, IJavaElement element) throws JavaModelException {
        ISourceRange r = ((ISourceReference) element).getNameRange();
        final Range range = JDTUtils.toRange(typeRoot, r.getOffset(), r.getLength());
        return range;
    }

    private static MainMethod constructMainMethod(ICompilationUnit typeRoot, IMethod method) throws JavaModelException {
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
