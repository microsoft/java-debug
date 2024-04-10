/*******************************************************************************
 * Copyright (c) 2018-2021 Microsoft Corporation and others.
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
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.Range;

import com.microsoft.java.debug.core.DebugException;

public class ResolveMainMethodHandler {
    /**
     * Resolve the main methods from the current file.
     * @return an array of main methods.
     */
    public static Object resolveMainMethods(List<Object> arguments, IProgressMonitor monitor) throws DebugException {
        if (monitor.isCanceled() || arguments == null || arguments.isEmpty()) {
            return Collections.emptyList();
        }

        // When the current document is changed, the language server will receive a didChange request about the changed text and then
        // trigger a background job to update the change to the CompilationUnit. Because of race condition, the resolveMainMethods may read
        // an old CompilationUnit. So add some waiting logic to wait the Document Update to finish first.
        try {
            Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
        } catch (OperationCanceledException e) {
            return Collections.emptyList();
        } catch (InterruptedException e) {
            // Do nothing.
        }

        if (monitor.isCanceled()) {
            return Collections.emptyList();
        }

        String uri = (String) arguments.get(0);
        final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
        if (monitor.isCanceled() || unit == null || unit.getResource() == null || !unit.getResource().exists()) {
            return Collections.emptyList();
        }

        try {
            return resolveMainMethodCore(unit);
        } catch (Exception e) {
            throw new DebugException("Failed to resolve main method codeLens: " + e.getMessage(), e);
        }
    }

    private static List<MainMethod> resolveMainMethodCore(ICompilationUnit compilationUnit) throws JavaModelException {
        List<MainMethod> result = new ArrayList<>();
        for (IMethod method : searchMainMethods(compilationUnit)) {
            MainMethod mainMethod = extractMainMethodInfo(compilationUnit, method);
            if (mainMethod != null) {
                result.add(mainMethod);
            }
        }

        return result;
    }

    private static List<IMethod> searchMainMethods(ICompilationUnit compilationUnit) throws JavaModelException {
        List<IMethod> result = new ArrayList<>();
        for (IType type : getPotentialMainClassTypes(compilationUnit)) {
            IMethod method = getMainMethod(type);
            if (method != null) {
                result.add(method);
            }
        }

        return result;
    }

    /**
     * Returns the main method defined in the type.
     */
    public static IMethod getMainMethod(IType type) throws JavaModelException {
        boolean allowInstanceMethod = isInstanceMainMethodSupported(type);
        List<IMethod> methods = new ArrayList<>();
        for (IMethod method : type.getMethods()) {
            if (method instanceof SourceMethod
                && ((SourceMethod) method).isMainMethodCandidate()) {
                methods.add(method);
            }

            if (method.isMainMethod()) {
                methods.add(method);
            }

            if (!allowInstanceMethod && !methods.isEmpty()) {
                return methods.get(0);
            }
        }

        if (!methods.isEmpty()) {
            methods.sort((method1, method2) -> {
                return getMainMethodPriority(method1) - getMainMethodPriority(method2);
            });

            return methods.get(0);
        }

        return null;
    }

    private static boolean isInstanceMainMethodSupported(IType type) {
        Map<String, String> options = type.getJavaProject().getOptions(true);
        return CompilerOptions.versionToJdkLevel(options.get(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM)) >= ClassFileConstants.JDK21;
    }

    /**
     * See Java 22 JEP 463 https://openjdk.org/jeps/463.
     * It searches the main method in the launched class by following a specific order:
     * - If the launched class contains a main method with a String[] parameter then choose that method.
     * - Otherwise, if the class contains a main method with no parameters then choose that method.
     */
    private static int getMainMethodPriority(IMethod method) {
        String[] params = method.getParameterTypes();
        return params.length == 1 ?  1 : 2;
    }

    private static List<IType> getPotentialMainClassTypes(ICompilationUnit compilationUnit) throws JavaModelException {
        List<IType> result = new ArrayList<>();
        IType[] topLevelTypes = compilationUnit.getTypes();
        result.addAll(Arrays.asList(topLevelTypes));
        for (IType type : topLevelTypes) {
            result.addAll(getPotentialMainClassTypesInChildren(type));
        }

        return result;
    }

    private static List<IType> getPotentialMainClassTypesInChildren(IType type) throws JavaModelException {
        IType[] children = type.getTypes();
        if (children.length == 0) {
            return Collections.emptyList();
        }

        List<IType> result = new ArrayList<>();
        for (IType child : children) {
            // main method can only exist in the static class or top level class.
            if (child.isClass() && Flags.isStatic(child.getFlags())) {
                result.add(child);
                result.addAll(getPotentialMainClassTypesInChildren(child));
            }
        }

        return result;
    }

    private static MainMethod extractMainMethodInfo(ICompilationUnit typeRoot, IMethod method) throws JavaModelException {
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
        return JDTUtils.toRange((IOpenable) typeRoot, r.getOffset(), r.getLength());
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
