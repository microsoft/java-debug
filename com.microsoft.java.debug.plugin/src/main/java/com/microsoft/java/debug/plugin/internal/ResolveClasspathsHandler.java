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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
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
    private static final String TEST_SCOPE = "test";
    private static final String MAVEN_SCOPE_ATTRIBUTE = "maven.scope";
    private static final String GRADLE_SCOPE_ATTRIBUTE = "gradle_scope";

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

                    if (!includeTestScope && forTestOnly(entries[j].getClasspathEntry())) {
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
     * There is an issue on isTest: it will return true if the scope is runtime, so we will this method for testing whether
     * the classpath entry is for test only.
     *
     * @param classpathEntry classpath entry
     * @return whether this classpath entry is only used in test
     */
    private static boolean forTestOnly(final IClasspathEntry classpathEntry) {
        for (IClasspathAttribute attribute : classpathEntry.getExtraAttributes()) {
            if (GRADLE_SCOPE_ATTRIBUTE.equals(attribute.getName()) || MAVEN_SCOPE_ATTRIBUTE.equals(attribute.getName())) {
                return TEST_SCOPE.equals(attribute.getValue());
            }
        }
        return classpathEntry.isTest();
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
        List<IJavaElement> testFolders = new ArrayList<IJavaElement>();
        try {
            IPackageFragmentRoot[] packageFragmentRoot = project.getPackageFragmentRoots();
            for (int i = 0; i < packageFragmentRoot.length; i++) {
                if (packageFragmentRoot[i].getElementType() == IJavaElement.PACKAGE_FRAGMENT_ROOT
                        && packageFragmentRoot[i].getKind() == IPackageFragmentRoot.K_SOURCE) {
                    IClasspathEntry cpe = packageFragmentRoot[i].getResolvedClasspathEntry();
                    if (forTestOnly(cpe)) {
                        testFolders.add(packageFragmentRoot[i]);
                    }
                }
            }
        } catch (JavaModelException e) {
            // ignore
        }
        if (!testFolders.isEmpty()) {
            try {

                List<Object> mainClassesInTestFolder = new ArrayList<>();
                SearchPattern pattern = SearchPattern.createPattern(mainClass, IJavaSearchConstants.CLASS,
                        IJavaSearchConstants.DECLARATIONS,
                        SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_EXACT_MATCH);
                SearchEngine searchEngine = new SearchEngine();
                IJavaSearchScope scope = SearchEngine
                        .createJavaSearchScope(testFolders.toArray(new IJavaElement[0]), constraints);
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
