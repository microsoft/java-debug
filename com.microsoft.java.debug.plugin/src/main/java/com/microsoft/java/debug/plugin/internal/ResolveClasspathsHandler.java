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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
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
    public static List<IJavaProject> getJavaProjectFromType(String fullyQualifiedTypeName) throws CoreException {
        // If only one Java project exists in the whole workspace, return the project directly.
        List<IJavaProject> javaProjects = JdtUtils.listJavaProjects(ResourcesPlugin.getWorkspace().getRoot());
        if (javaProjects.size() <= 1) {
            return javaProjects;
        }

        String[] splitItems = fullyQualifiedTypeName.split("/");
        // If the main class name contains the module name, should trim the module info.
        if (splitItems.length == 2) {
            fullyQualifiedTypeName = splitItems[1];
        }
        final String moduleName = splitItems.length == 2 ? splitItems[0] : null;

        // Search the associated project for the type.
        List<IJavaProject> projects = searchJavaProjectFromType(fullyQualifiedTypeName, moduleName);
        if (projects.isEmpty()) {
            String className = fullyQualifiedTypeName.substring(fullyQualifiedTypeName.lastIndexOf('.') + 1);
            // See the bug https://github.com/Microsoft/vscode-java-debug/issues/427
            // If the target class is an inner class, its fully qualified name will contain $, but the search engine doesn't support $ separated class name.
            // Add an extra search from main method.
            if (className.indexOf('$') > 0) {
                return searchJavaProjectFromMainMethod(fullyQualifiedTypeName, moduleName);
            }
        }

        return projects;
    }

    private static List<IJavaProject> searchJavaProjectFromType(String fullyQualifiedTypeName, String moduleName) throws CoreException {
        SearchPattern pattern = SearchPattern.createPattern(
                fullyQualifiedTypeName,
                IJavaSearchConstants.CLASS,
                IJavaSearchConstants.DECLARATIONS,
                SearchPattern.R_EXACT_MATCH);

        List<IJavaProject> projects = searchJavaProject(pattern, (SearchMatch match) -> {
            Object element = match.getElement();
            if (element instanceof IJavaElement) {
                IJavaProject project = ((IJavaElement) element).getJavaProject();
                if (moduleName == null || moduleName.equals(JdtUtils.getModuleName(project))) {
                    return project;
                }
            }

            return null;
        });

        return projects.stream().distinct().collect(Collectors.toList());
    }

    private static List<IJavaProject> searchJavaProjectFromMainMethod(String fullyQualifiedTypeName, String moduleName) throws CoreException {
        SearchPattern pattern = SearchPattern.createPattern(
                "main(String[]) void",
                IJavaSearchConstants.METHOD,
                IJavaSearchConstants.DECLARATIONS,
                SearchPattern.R_EXACT_MATCH);

        List<IJavaProject> projects = searchJavaProject(pattern, (SearchMatch match) -> {
            Object element = match.getElement();
            if (element instanceof IMethod) {
                IMethod method = (IMethod) element;
                try {
                    if (method.isMainMethod() && method.getDeclaringType().getFullyQualifiedName().equals(fullyQualifiedTypeName)) {
                        IJavaProject project = method.getJavaProject();
                        if (moduleName == null || moduleName.equals(JdtUtils.getModuleName(project))) {
                            return project;
                        }
                    }
                } catch (JavaModelException e) {
                    // ignore
                }
            }

            return null;
        });

        return projects.stream().distinct().collect(Collectors.toList());
    }

    private static List<IJavaProject> searchJavaProject(SearchPattern searchPattern, Function<SearchMatch, IJavaProject> handleSearchMatch)
        throws CoreException {
        List<IJavaProject> projects = new ArrayList<>();
        IJavaSearchScope searchScope = SearchEngine.createWorkspaceScope();
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                IJavaProject accept = handleSearchMatch.apply(match);
                if (accept != null) {
                    projects.add(accept);
                }
            }
        };
        SearchEngine searchEngine = new SearchEngine();
        searchEngine.search(searchPattern,
                new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()},
                searchScope,
                requestor,
                null /* progress monitor */);

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
        if (StringUtils.isNotBlank(projectName)) {
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

        return computeClassPath(project, isMainClassInTestFolder(project, mainClass));
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
    private static String[][] computeClassPath(IJavaProject javaProject, boolean includeTestScope)
            throws CoreException {
        if (javaProject == null) {
            throw new IllegalArgumentException("javaProject is null");
        }
        String[][] result = new String[2][];
        if (JavaRuntime.isModularProject(javaProject)) {
            result[0] = computeDefaultRuntimeClassPath(javaProject, includeTestScope);
            result[1] = new String[0];
        } else {
            result[0] = new String[0];
            result[1] = computeDefaultRuntimeClassPath(javaProject, includeTestScope);
        }
        return result;
    }

    private static String[] computeDefaultRuntimeClassPath(IJavaProject jproject, boolean includeTestScope)
            throws CoreException {
        IRuntimeClasspathEntry[] unresolved = JavaRuntime.computeUnresolvedRuntimeClasspath(jproject);
        Set<String> resolved = new LinkedHashSet<String>();
        for (int i = 0; i < unresolved.length; i++) {
            IRuntimeClasspathEntry entry = unresolved[i];
            if (entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES) {
                IRuntimeClasspathEntry[] entries = JavaRuntime.resolveRuntimeClasspathEntry(entry, jproject,
                        !includeTestScope);
                for (int j = 0; j < entries.length; j++) {

                    if (!includeTestScope && JdtUtils.isTest(entries[j].getClasspathEntry())) {
                        continue;
                    }
                    String location = entries[j].getLocation();
                    if (location != null) {
                        // remove duplicate classpath
                        resolved.add(location);
                    }
                }
            }
        }
        return resolved.toArray(new String[resolved.size()]);
    }


    /**
     * Test whether the main class is located in test folders.
     * @param project the java project containing the main class
     * @param mainClass the main class name
     * @return whether the main class is located in test folders
     */
    private static boolean isMainClassInTestFolder(IJavaProject project, String mainClass) {
        // get a list of test folders and check whether main class is here
        int constraints = IJavaSearchScope.SOURCES;
        IJavaElement[] testFolders = JdtUtils.getTestPackageFragmentRoots(project);
        if (testFolders.length > 0) {
            try {

                List<Object> mainClassesInTestFolder = new ArrayList<>();
                SearchPattern pattern = SearchPattern.createPattern(mainClass, IJavaSearchConstants.CLASS,
                        IJavaSearchConstants.DECLARATIONS,
                        SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_EXACT_MATCH);
                SearchEngine searchEngine = new SearchEngine();
                IJavaSearchScope scope = SearchEngine.createJavaSearchScope(testFolders, constraints);
                SearchRequestor requestor = new SearchRequestor() {
                    @Override
                    public void acceptSearchMatch(SearchMatch match) {
                        Object element = match.getElement();
                        if (element instanceof IJavaElement) {
                            mainClassesInTestFolder.add(element);
                        }
                    }
                };

                searchEngine.search(pattern, new SearchParticipant[] {
                            SearchEngine.getDefaultSearchParticipant()
                    }, scope, requestor, null /* progress monitor */);

                return !mainClassesInTestFolder.isEmpty();
            } catch (Exception e) {
                logger.log(Level.SEVERE, String.format("Searching the main class failure: %s", e.toString()), e);
            }
        }
        return false;
    }
}
