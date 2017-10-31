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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
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
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

import com.microsoft.java.debug.core.Configuration;

public class ResolveMainClassHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    /**
     * resolve main class and project name.
     * @return an array of main class and project name
     * @throws CoreException when there are errors when resolving main class.
     */
    public Object resolveMainClass() throws CoreException {
        return resolveMainClassCore();
    }

    private List<ResolutionItem> resolveMainClassCore() throws CoreException {
        IJavaSearchScope searchScope = SearchEngine.createWorkspaceScope();
        SearchPattern pattern = SearchPattern.createPattern("main(String[]) void", IJavaSearchConstants.METHOD,
                IJavaSearchConstants.DECLARATIONS, SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_EXACT_MATCH);
        ArrayList<ResolutionItem> res = new ArrayList<>();
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
                                    res.add(new ResolutionItem(mainClass, projectName));
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
        searchEngine.search(pattern, new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()},
                searchScope, requestor, null /* progress monitor */);
        return res.stream().distinct().collect(Collectors.toList());
    }

    private class ResolutionItem {
        private String mainClass;
        private String projectName;

        public ResolutionItem(String mainClass, String projectName) {
            this.mainClass = mainClass;
            this.projectName = projectName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof ResolutionItem) {
                ResolutionItem item = (ResolutionItem) o;
                if (mainClass != null ? !mainClass.equals(item.mainClass) : item.mainClass != null) {
                    return false;
                }
                if (projectName != null ? !projectName.equals(item.projectName) : item.projectName != null) {
                    return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (mainClass == null ? 0 : mainClass.hashCode()) * 13 + (projectName == null ? 0 : projectName.hashCode());
        }
    }
}
