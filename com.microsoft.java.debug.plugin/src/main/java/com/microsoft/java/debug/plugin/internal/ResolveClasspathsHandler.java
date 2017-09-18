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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.launching.JavaRuntime;

import com.microsoft.java.debug.core.Configuration;

public class ResolveClasspathsHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    /**
     * Resolves class path for a java project.
     * @param arguments a list contains the main class name and  project name
     * @return the class paths entries
     * @throws Exception when there are any errors during resolving class path
     */
    public String[] resolveClasspaths(List<Object> arguments) throws Exception {
        try {
            return computeClassPath((String) arguments.get(0), (String) arguments.get(1));
        } catch (CoreException e) {
            logger.severe("Failed to resolve classpath: " + e.getMessage());
            throw new Exception("Failed to resolve classpath: " + e.getMessage(), e);
        }
    }

    /**
     * Get java project from name.
     *
     * @param projectName
     *            project name
     * @return java project
     * @throws CoreException
     *             CoreException
     */
    private static IJavaProject getJavaProjectFromName(String projectName) throws CoreException {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        if (!project.exists()) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID,
                    String.format("Cannot find the project with name '%s'.", projectName)));
        }
        if (!project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID,
                    String.format("The project '%s' does not have java nature enabled.", projectName)));
        }
        IJavaProject javaProject = JavaCore.create(project);
        return javaProject;
    }

    /**
     * Get java project from type.
     *
     * @param typeFullyQualifiedName
     *            fully qualified name of type
     * @return java project
     * @throws CoreException
     *             CoreException
     */
    private static List<IJavaProject> getJavaProjectFromType(String typeFullyQualifiedName) throws CoreException {
        SearchPattern pattern = SearchPattern.createPattern(typeFullyQualifiedName, IJavaSearchConstants.TYPE,
                IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
        ArrayList<IJavaProject> projects = new ArrayList<>();
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                Object element = match.getElement();
                if (element instanceof IJavaElement) {
                    projects.add(((IJavaElement) element).getJavaProject());
                }
            }
        };
        SearchEngine searchEngine = new SearchEngine();
        searchEngine.search(pattern, new SearchParticipant[] {
            SearchEngine.getDefaultSearchParticipant() }, scope,
            requestor, null /* progress monitor */);

        return projects;
    }

    /**
     * Accord to the project name and the main class, compute runtime classpath.
     *
     * @param mainClass
     *            fully qualified class name
     * @param projectName
     *            project name
     * @return class path
     * @throws CoreException
     *             CoreException
     */
    private static String[] computeClassPath(String mainClass, String projectName) throws CoreException {
        IJavaProject project = null;
        // if type exists in multiple projects, debug configuration need provide
        // project name.
        if (projectName != null) {
            project = getJavaProjectFromName(projectName);
        } else {
            List<IJavaProject> projects = getJavaProjectFromType(mainClass);
            if (projects.size() == 0) {
                throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID,
                        String.format("Main class '%s' doesn't exist in the workspace.", mainClass)));
            }
            if (projects.size() > 1) {
                throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID,
                        String.format(
                                "Main class '%s' isn't unique in the workspace, please pass in specified projectname.",
                                mainClass)));
            }
            project = projects.get(0);
        }
        return computeClassPath(project);
    }

    /**
     * Compute runtime classpath of a java project.
     *
     * @param javaProject
     *            java project
     * @return class path
     * @throws CoreException
     *             CoreException
     */
    private static String[] computeClassPath(IJavaProject javaProject) throws CoreException {
        if (javaProject == null) {
            throw new IllegalArgumentException("javaProject is null");
        }
        return JavaRuntime.computeDefaultRuntimeClassPath(javaProject);
    }
}
