/*******************************************************************************
 * Copyright (c) 2017-2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.internal.core.LaunchConfiguration;
import org.eclipse.debug.internal.core.LaunchConfigurationInfo;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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
            if (arguments.size() == 3) {
                return computeClassPath((String) arguments.get(0), (String) arguments.get(1), (String) arguments.get(2));
            }
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
        final String className = fullyQualifiedTypeName;
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
                if (element instanceof IType) {
                    IType type = (IType) element;
                    IJavaProject project = type.getJavaProject();
                    if (className.equals(type.getFullyQualifiedName())
                            && (moduleName == null || moduleName.equals(JdtUtils.getModuleName(project)))) {
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
        return computeClassPath(mainClass, projectName, null);
    }

    /**
     * Accord to the project name and the main class, compute runtime classpath.
     *
     * @param mainClass
     *            fully qualified class name
     * @param projectName
     *            project name
     * @param scope
     *            scope of the classpath
     * @return class path
     * @throws CoreException
     *             CoreException
     */
    private static String[][] computeClassPath(String mainClass, String projectName, String scope) throws CoreException {
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

        if ("test".equals(scope)) {
            return computeClassPath(project, mainClass, false /*excludeTestCode*/, Collections.EMPTY_LIST);
        } else if ("runtime".equals(scope)) {
            return computeClassPath(project, mainClass, true /*excludeTestCode*/, Collections.EMPTY_LIST);
        }

        IJavaElement testElement = findMainClassInTestFolders(project, mainClass);
        List<IResource> mappedResources = (testElement != null && testElement.getResource() != null)
                ? Arrays.asList(testElement.getResource()) : Collections.EMPTY_LIST;
        return computeClassPath(project, mainClass, testElement == null, mappedResources);
    }

    /**
     * Compute runtime classpath of a java project.
     *
     * @param javaProject java project
     * @param excludeTestCode whether to exclude the test code and test dependencies
     * @param mappedResources the associated resources with the application
     * @return class path
     * @throws CoreException
     *             CoreException
     */
    private static String[][] computeClassPath(IJavaProject javaProject, String mainType, boolean excludeTestCode, List<IResource> mappedResources)
            throws CoreException {
        if (javaProject == null) {
            throw new IllegalArgumentException("javaProject is null");
        }

        ILaunchConfiguration launchConfig = new JavaApplicationLaunchConfiguration(javaProject.getProject(), mainType, excludeTestCode, mappedResources);
        IRuntimeClasspathEntry[] unresolved = JavaRuntime.computeUnresolvedRuntimeClasspath(launchConfig);
        IRuntimeClasspathEntry[] resolved = JavaRuntime.resolveRuntimeClasspath(unresolved, launchConfig);
        Set<String> classpaths = new LinkedHashSet<>();
        Set<String> modulepaths = new LinkedHashSet<>();
        for (IRuntimeClasspathEntry entry : resolved) {
            String location = entry.getLocation();
            if (location != null) {
                if (entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES
                        || entry.getClasspathProperty() == IRuntimeClasspathEntry.CLASS_PATH) {
                    classpaths.add(location);
                } else if (entry.getClasspathProperty() == IRuntimeClasspathEntry.MODULE_PATH) {
                    modulepaths.add(location);
                }
            }
        }

        return new String[][] {
            modulepaths.toArray(new String[modulepaths.size()]),
            classpaths.toArray(new String[classpaths.size()])
        };
    }

    /**
     * Try to find the associated java element with the main class from the test folders.
     *
     * @param project the java project containing the main class
     * @param mainClass the main class name
     * @return the associated java element
     */
    private static IJavaElement findMainClassInTestFolders(IJavaProject project, String mainClass) {
        if (project == null || StringUtils.isBlank(mainClass)) {
            return null;
        }

        // get a list of test folders and check whether main class is here
        int constraints = IJavaSearchScope.SOURCES;
        IJavaElement[] testFolders = JdtUtils.getTestPackageFragmentRoots(project);
        if (testFolders.length > 0) {
            try {

                List<IJavaElement> mainClassesInTestFolder = new ArrayList<>();
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
                            mainClassesInTestFolder.add((IJavaElement) element);
                        }
                    }
                };

                searchEngine.search(pattern, new SearchParticipant[] {
                            SearchEngine.getDefaultSearchParticipant()
                    }, scope, requestor, null /* progress monitor */);

                if (!mainClassesInTestFolder.isEmpty()) {
                    return mainClassesInTestFolder.get(0);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, String.format("Searching the main class failure: %s", e.toString()), e);
            }
        }

        return null;
    }

    private static class JavaApplicationLaunchConfiguration extends LaunchConfiguration {
        public static final String JAVA_APPLICATION_LAUNCH = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<launchConfiguration type=\"%s\">\n"
                + "<listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_PATHS\">\n"
                + "</listAttribute>\n"
                + "<listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_TYPES\">\n"
                + "</listAttribute>\n"
                + "</launchConfiguration>";
        private IProject project;
        private String mainType;
        private boolean excludeTestCode;
        private List<IResource> mappedResources;
        private String classpathProvider;
        private String sourcepathProvider;
        private LaunchConfigurationInfo launchInfo;

        protected JavaApplicationLaunchConfiguration(IProject project, String mainType, boolean excludeTestCode, List<IResource> mappedResources)
            throws CoreException {
            super(String.valueOf(new Date().getTime()), null, false);
            this.project = project;
            this.mainType = mainType;
            this.excludeTestCode = excludeTestCode;
            this.mappedResources = mappedResources;
            if (ProjectUtils.isMavenProject(project)) {
                classpathProvider = "org.eclipse.m2e.launchconfig.classpathProvider";
                sourcepathProvider = "org.eclipse.m2e.launchconfig.sourcepathProvider";
            } else if (ProjectUtils.isGradleProject(project)) {
                classpathProvider = "org.eclipse.buildship.core.classpathprovider";
            }

            // Since MavenRuntimeClasspathProvider will only including test entries when:
            // 1. Launch configuration is JUnit/TestNG type
            // 2. Mapped resource is in test path.
            // That's why we use JUnit launch configuration here to make sure the result is right when excludeTestCode is false.
            String launchXml = null;
            if (!excludeTestCode && mappedResources.isEmpty()) {
                launchXml = String.format(JAVA_APPLICATION_LAUNCH, "org.eclipse.jdt.junit.launchconfig");
            } else {
                launchXml = String.format(JAVA_APPLICATION_LAUNCH, "org.eclipse.jdt.launching.localJavaApplication");
            }
            this.launchInfo = new JavaLaunchConfigurationInfo(launchXml);
        }

        @Override
        public boolean getAttribute(String attributeName, boolean defaultValue) throws CoreException {
            if (IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE.equalsIgnoreCase(attributeName)) {
                return excludeTestCode;
            }

            return super.getAttribute(attributeName, defaultValue);
        }

        @Override
        public String getAttribute(String attributeName, String defaultValue) throws CoreException {
            if (IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME.equalsIgnoreCase(attributeName)) {
                return project.getName();
            } else if (IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER.equalsIgnoreCase(attributeName)) {
                return classpathProvider;
            } else if (IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER.equalsIgnoreCase(attributeName)) {
                return sourcepathProvider;
            } else if (IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME.equalsIgnoreCase(attributeName)) {
                return mainType;
            }

            return super.getAttribute(attributeName, defaultValue);
        }

        @Override
        public IResource[] getMappedResources() throws CoreException {
            return mappedResources.toArray(new IResource[0]);
        }

        @Override
        protected LaunchConfigurationInfo getInfo() throws CoreException {
            return this.launchInfo;
        }
    }

    private static class JavaLaunchConfigurationInfo extends LaunchConfigurationInfo {
        public JavaLaunchConfigurationInfo(String launchXml) {
            super();
            try {
                DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                parser.setErrorHandler(new DefaultHandler());
                StringReader reader = new StringReader(launchXml);
                InputSource source = new InputSource(reader);
                Element root = parser.parse(source).getDocumentElement();
                initializeFromXML(root);
            } catch (ParserConfigurationException | SAXException | IOException | CoreException e) {
                // do nothing
            }
        }
    }
}
