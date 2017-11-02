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

package com.microsoft.java.debug.plugin.internal;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;

public class JdtUtils {

    /**
     * Returns the module this project represents or null if the Java project doesn't represent any named module.
     */
    public static String getModuleName(IJavaProject project) {
        if (project == null || !JavaRuntime.isModularProject(project)) {
            return null;
        }
        IModuleDescription module;
        try {
            module = project.getModuleDescription();
        } catch (JavaModelException e) {
            return null;
        }
        return module == null ? null : module.getElementName();
    }

    /**
     * Check if the project is a java project or not.
     */
    public static boolean isJavaProject(IProject project) {
        if (project == null || !project.exists()) {
            return false;
        }
        try {
            if (!project.isNatureEnabled(JavaCore.NATURE_ID)) {
                return false;
            }
        } catch (CoreException e) {
            return false;
        }
        return true;
    }

    /**
     * If the project represents a java project, then convert it to a java project.
     * Otherwise, return null.
     */
    public static IJavaProject getJavaProject(IProject project) {
        if (isJavaProject(project)) {
            return JavaCore.create(project);
        }
        return null;
    }

    /**
     * Given the project name, return the corresponding java project model.
     * If the project doesn't exist or not a java project, return null.
     */
    public static IJavaProject getJavaProject(String projectName) {
        if (projectName == null) {
            return null;
        }
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        return getJavaProject(project);
    }
}
