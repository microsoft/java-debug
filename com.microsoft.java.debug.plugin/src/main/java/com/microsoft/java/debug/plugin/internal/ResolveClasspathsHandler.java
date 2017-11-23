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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
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
    public String[][] resolveClasspaths(List<Object> arguments) throws Exception {
        try {
            return computeClassPath((String) arguments.get(0), (String) arguments.get(1));
        } catch (CoreException e) {
            logger.log(Level.SEVERE, "Failed to resolve classpath: " + e.getMessage(), e);
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
        IJavaProject javaProject = JdtUtils.getJavaProject(projectName);
        if (javaProject == null) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID,
                    String.format("The project '%s' is not a valid java project.", projectName)));
        }
        return javaProject;
    }

    /**
     * Get java project from type.
     *
     * @param fullyQualifiedTypeName
     *            fully qualified name of type
     * @return java project
     * @throws CoreException
     *             CoreException
     */
    private static List<IJavaProject> getJavaProjectFromType(String fullyQualifiedTypeName) throws CoreException {
        String[] splitItems = fullyQualifiedTypeName.split("/");
        // If the main class name contains the module name, should trim the module info.
        if (splitItems.length == 2) {
            fullyQualifiedTypeName = splitItems[1];
        }
        final String moduleName = splitItems.length == 2 ? splitItems[0] : null;

        SearchPattern pattern = SearchPattern.createPattern(
                fullyQualifiedTypeName,
                IJavaSearchConstants.TYPE,
                IJavaSearchConstants.DECLARATIONS,
                SearchPattern.R_EXACT_MATCH);
        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
        ArrayList<IJavaProject> projects = new ArrayList<>();
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                Object element = match.getElement();
                if (element instanceof IJavaElement) {
                    IJavaProject project = ((IJavaElement) element).getJavaProject();
                    if (moduleName == null || moduleName.equals(JdtUtils.getModuleName(project))) {
                        projects.add(project);
                    }
                }
            }
        };
        SearchEngine searchEngine = new SearchEngine();
        searchEngine.search(pattern, new SearchParticipant[] {
            SearchEngine.getDefaultSearchParticipant() }, scope,
            requestor, null /* progress monitor */);

        return projects.stream().distinct().collect(Collectors.toList());
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
    private static String[][] computeClassPath(String mainClass, String projectName) throws CoreException {
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
    private static String[][] computeClassPath(IJavaProject javaProject) throws CoreException {
        if (javaProject == null) {
            throw new IllegalArgumentException("javaProject is null");
        }
        String[][] result = new String[3][];
        if (JavaRuntime.isModularProject(javaProject)) {
            result[0] = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);
            result[1] = new String[0];
        } else {
            result[0] = new String[0];
            result[1] = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);
        }
        result[2] = new String[] {javaProject.getProject().getName()};
        return result;
    }
}
