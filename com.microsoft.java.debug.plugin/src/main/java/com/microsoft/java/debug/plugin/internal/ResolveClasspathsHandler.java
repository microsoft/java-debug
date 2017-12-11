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
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.UserErrorException;

public class ResolveClasspathsHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    /**
     * Resolves class path for a java project.
     *
     * @param arguments
     *            a list contains the main class name and project name
     * @return the class paths entries
     * @throws DebugException
     *             when there are any errors during resolving class path
     */
    public String[][] resolveClasspaths(List<Object> arguments) throws DebugException {
        return computeClassPath((String) arguments.get(0), (String) arguments.get(1));
    }

    /**
     * Get java project from name.
     *
     * @param projectName project name
     * @return java project
     * @throws DebugException when the project is not found or invalid.
     */
    private static IJavaProject getJavaProjectFromName(String projectName) throws DebugException {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        if (JdtUtils.isJavaProject(project)) {
            return JdtUtils.getJavaProject(projectName);
        }
        if (project == null || !project.exists()) {
            throw new UserErrorException(String.format("The project '%s' doesn't exist.", projectName));
        } else {
            throw new UserErrorException(String.format("The project '%s' is not a valid java project.", projectName));
        }
    }

    /**
     * Get java project from type.
     *
     * @param fullyQualifiedTypeName the fully qualified name of type
     * @return a list of java projects contains specified class name.
     * @throws DebugException when there is anything error when searching the projects for specified class name.
     */
    private static List<IJavaProject> getJavaProjectFromType(String fullyQualifiedTypeName) throws DebugException {
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
        try {
            searchEngine.search(pattern, new SearchParticipant[] {
                    SearchEngine.getDefaultSearchParticipant() }, scope, requestor, null /* progress monitor */);
        } catch (CoreException e) {
            throw new DebugException(String.format("Encounter %s during querying for class %s: %s", e.getClass().getName(), pattern, e.getMessage()));
        }

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
     * @throws DebugException when there is any error finding the class path.
     */
    private static String[][] computeClassPath(String mainClass, String projectName) throws DebugException {
        IJavaProject project = null;
        // if type exists in multiple projects, debug configuration need provide
        // project name.
        if (!StringUtils.isBlank(projectName)) {
            project = getJavaProjectFromName(projectName);
        } else {
            List<IJavaProject> projects = getJavaProjectFromType(mainClass);
            if (projects.size() == 0) {
                throw new UserErrorException(String.format("Main class '%s' doesn't exist in the workspace.", mainClass));
            }

            project = projects.get(0);

            if (projects.size() > 1) {
                // when mainClass is located in
                logger.warning(String.format("Main class '%s' isn't unique in the workspace"
                        + ", use project %s for default, please specify projectName in launch.json if you want to use other project.",
                        mainClass, project.getProject().getName()));
            }

        }
        return computeClassPath(project);
    }

    /**
     * Compute runtime classpath of a java project.
     *
     * @param javaProject
     *            java project
     * @return class path
     * @throws DebugException when there is any error finding the class path.
     */
    private static String[][] computeClassPath(IJavaProject javaProject) throws DebugException {
        if (javaProject == null) {
            throw new IllegalArgumentException("javaProject is null");
        }
        String[][] result = new String[2][];
        try {
            if (JavaRuntime.isModularProject(javaProject)) {
                result[0] = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);
                result[1] = new String[0];
            } else {
                result[0] = new String[0];
                result[1] = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);
            }
        } catch (CoreException e) {
            throw new UserErrorException(String.format("Encounter errors when computing classpath for project %s.", javaProject.getProject().getName()));
        }
        return result;
    }
}
