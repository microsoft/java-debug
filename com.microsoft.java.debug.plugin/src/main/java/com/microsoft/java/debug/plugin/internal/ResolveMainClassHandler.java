/*******************************************************************************
 * Copyright (c) 2017-2021 Microsoft Corporation and others.
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
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
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

import com.microsoft.java.debug.core.Configuration;

public class ResolveMainClassHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final int CONFIGERROR_INVALID_CLASS_NAME = 1;
    private static final int CONFIGERROR_MAIN_CLASS_NOT_EXIST = 2;
    private static final int CONFIGERROR_MAIN_CLASS_NOT_UNIQUE = 3;
    private static final int CONFIGERROR_INVALID_JAVA_PROJECT = 4;

    /**
     * resolve main class and project name.
     * @return an array of main class and project name
     */
    public Object resolveMainClass(List<Object> arguments) {
        return resolveMainClassCore(arguments);
    }

    /**
     * Validate whether the mainClass and projectName is correctly configured or not. If not, report the validation error and provide the quick fix proposal.
     *
     * @param arguments the mainClass and projectName configs.
     * @return the validation response.
     * @throws Exception when there are any errors during validating the mainClass and projectName.
     */
    public Object validateLaunchConfig(List<Object> arguments) throws Exception {
        try {
            return validateLaunchConfigCore(arguments);
        } catch (CoreException ex) {
            logger.log(Level.SEVERE, "Failed to validate launch config: " + ex.getMessage(), ex);
            throw new Exception("Failed to validate launch config: " + ex.getMessage(), ex);
        }
    }

    private List<ResolutionItem> resolveMainClassCore(List<Object> arguments) {
        IPath rootPath = null;
        if (arguments != null && arguments.size() > 0 && arguments.get(0) != null) {
            rootPath = ResourceUtils.filePathFromURI((String) arguments.get(0));
        }
        final ArrayList<IPath> targetProjectPath = new ArrayList<>();
        if (rootPath != null) {
            targetProjectPath.add(rootPath);
        }
        IJavaSearchScope searchScope = SearchEngine.createWorkspaceScope();
        SearchPattern pattern = SearchPattern.createPattern("main(String[]) void", IJavaSearchConstants.METHOD,
                IJavaSearchConstants.DECLARATIONS, SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_EXACT_MATCH);
        final List<ResolutionItem> res = new ArrayList<>();
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                Object element = match.getElement();
                if (element instanceof IMethod) {
                    IMethod method = (IMethod) element;
                    try {
                        if (method.isMainMethod()) {
                            IResource resource = method.getResource();
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
                                    if (projectName == null
                                        || targetProjectPath.isEmpty()
                                        || ResourceUtils.isContainedIn(project.getLocation(), targetProjectPath)
                                        || isContainedInInvisibleProject(project, targetProjectPath)) {
                                        String filePath = null;

                                        if (match.getResource() instanceof IFile) {
                                            try {
                                                filePath = match.getResource().getLocation().toOSString();
                                            } catch (Exception ex) {
                                                // ignore
                                            }
                                        }
                                        res.add(new ResolutionItem(mainClass, projectName, filePath));
                                    }
                                }
                            }
                        }
                    } catch (JavaModelException e) {
                        // ignore
                    }
                }
            }
        };
        SearchEngine searchEngine = new SearchEngine();
        try {
            searchEngine.search(pattern, new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()},
                    searchScope, requestor, null /* progress monitor */);
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("Searching the main class failure: %s", e.toString()), e);
        }

        List<ResolutionItem> resolutions = res.stream().distinct().collect(Collectors.toList());
        Collections.sort(resolutions);
        return resolutions;
    }

    private boolean isContainedInInvisibleProject(IProject project, Collection<IPath> rootPaths) {
        if (project == null) {
            return false;
        }

        IFolder workspaceLinkFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK);
        return workspaceLinkFolder.exists() && ResourceUtils.isContainedIn(workspaceLinkFolder.getLocation(), rootPaths);
    }

    private ValidationResponse validateLaunchConfigCore(List<Object> arguments) throws CoreException {
        ValidationResponse response = new ValidationResponse();

        String mainClass = null;
        String projectName = null;
        boolean containsExternalClasspaths = false;
        if (arguments != null) {
            if (arguments.size() > 1) {
                mainClass = (String) arguments.get(1);
            }
            if (arguments.size() > 2) {
                projectName = (String) arguments.get(2);
            }
            if (arguments.size() > 3) {
                containsExternalClasspaths = (boolean) arguments.get(3);
            }
        }

        response.mainClass = validateMainClass(mainClass, projectName, containsExternalClasspaths);
        response.projectName = validateProjectName(mainClass, projectName);

        if (!response.mainClass.isValid || !response.projectName.isValid) {
            response.proposals = computeProposals(arguments, mainClass, projectName);
        }

        return response;
    }

    private ValidationResult validateMainClass(final String mainClass, final String projectName, boolean containsExternalClasspaths) throws CoreException {
        if (StringUtils.isEmpty(mainClass)) {
            return new ValidationResult(true);
        } else if (!isValidMainClassName(mainClass)) {
            return new ValidationResult(false, String.format("ConfigError: '%s' is not a valid class name.", mainClass),
                CONFIGERROR_INVALID_CLASS_NAME);
        }

        if (!containsExternalClasspaths && StringUtils.isEmpty(projectName)) {
            List<IJavaProject> javaProjects = searchClassInProjectClasspaths(mainClass);
            if (javaProjects.size() == 0) {
                return new ValidationResult(false, String.format("ConfigError: Main class '%s' doesn't exist in the workspace.", mainClass),
                    CONFIGERROR_MAIN_CLASS_NOT_EXIST);
            }
            if (javaProjects.size() > 1) {
                return new ValidationResult(false, String.format("ConfigError: Main class '%s' isn't unique in the workspace.", mainClass),
                    CONFIGERROR_MAIN_CLASS_NOT_UNIQUE);
            }
        }

        return new ValidationResult(true);
    }

    // Java command line supports two kinds of main class format: <mainclass> and <module>[/<mainclass>]
    private boolean isValidMainClassName(String mainClass) {
        if (StringUtils.isEmpty(mainClass)) {
            return true;
        }

        int index = mainClass.indexOf('/');
        if (index == -1) {
            return SourceVersion.isName(mainClass);
        }

        return SourceVersion.isName(mainClass.substring(0, index))
            && SourceVersion.isName(mainClass.substring(index + 1));
    }

    private List<IJavaProject> searchClassInProjectClasspaths(String fullyQualifiedClassName) throws CoreException {
        return ResolveClasspathsHandler.getJavaProjectFromType(fullyQualifiedClassName);
    }

    private ValidationResult validateProjectName(final String mainClass, final String projectName) {
        if (StringUtils.isEmpty(projectName)) {
            return new ValidationResult(true);
        }

        if (JdtUtils.getJavaProject(projectName) == null) {
            return new ValidationResult(false, String.format("ConfigError: The project '%s' is not a valid java project.", projectName),
                CONFIGERROR_INVALID_JAVA_PROJECT);
        }

        return new ValidationResult(true);
    }

    private List<ResolutionItem> computeProposals(List<Object> arguments, final String mainClass, final String projectName) {
        List<ResolutionItem> proposals = resolveMainClassCore(arguments);

        Collections.sort(proposals, new ProposalItemComparator((ResolutionItem item) -> {
            if (Objects.equals(item.mainClass, mainClass)) {
                return 1;
            } else if (Objects.equals(item.projectName, projectName)) {
                return 2;
            }

            return 999;
        }));

        return proposals;
    }

    private class ResolutionItem implements Comparable<ResolutionItem> {
        private String mainClass;
        private String projectName;
        private String filePath;

        public ResolutionItem(String mainClass, String projectName, String filePath) {
            this.mainClass = mainClass;
            this.projectName = projectName;
            this.filePath = filePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof ResolutionItem) {
                ResolutionItem item = (ResolutionItem) o;
                return Objects.equals(mainClass, item.mainClass)
                        && Objects.equals(projectName, item.projectName)
                        && Objects.equals(filePath, item.filePath);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mainClass, projectName, filePath);
        }

        @Override
        public int compareTo(ResolutionItem o) {
            if (isDefaultProject(this.projectName) && !isDefaultProject(o.projectName)) {
                return 1;
            } else if (!isDefaultProject(this.projectName) && isDefaultProject(o.projectName)) {
                return -1;
            }

            return (this.projectName + "|" + this.mainClass).compareTo(o.projectName + "|" + o.mainClass);
        }

        private boolean isDefaultProject(String projectName) {
            return StringUtils.isEmpty(projectName);
        }
    }

    class ProposalItemComparator implements Comparator<ResolutionItem> {
        Function<ResolutionItem, Integer> getRank;

        ProposalItemComparator(Function<ResolutionItem, Integer> getRank) {
            this.getRank = getRank;
        }

        @Override
        public int compare(ResolutionItem o1, ResolutionItem o2) {
            return getRank.apply(o1) - getRank.apply(o2);
        }
    }

    class ValidationResponse {
        ValidationResult mainClass;
        ValidationResult projectName;
        List<ResolutionItem> proposals;
    }

    class ValidationResult {
        boolean isValid;
        String message;
        int kind;

        ValidationResult(boolean isValid) {
            this.isValid = isValid;
        }

        ValidationResult(boolean isValid, String message, int kind) {
            this.isValid = isValid;
            this.message = message;
            this.kind = kind;
        }
    }
}
