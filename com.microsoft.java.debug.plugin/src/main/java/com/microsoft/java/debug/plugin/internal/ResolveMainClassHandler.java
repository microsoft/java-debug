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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.microsoft.java.debug.core.Configuration;

public class ResolveMainClassHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    /**
     * resolve main class and project name if not specified.
     * @param arguments a list of arguments including project files and project name
     * @return main class and project name
     * @throws CoreException when there are error when resolving main class.
     */
    public String[] resolveMainClass(List<Object> arguments) throws CoreException {
        List<String> projectNames;
        if (arguments.size() > 1 && arguments.get(1) != null) {
            // project name specified
            projectNames = new ArrayList<>();
            projectNames.add((String) arguments.get(1));
        } else {
            List<String> projectFiles = (List<String>) arguments.get(0);
            projectNames = resolveProjectName(projectFiles);
        }
        String[] res = {null, null};
        for (String projectName : projectNames) {
            String mainClass = resolveMainClassCore(projectName);
            if (mainClass != null) {
                res[0] = mainClass;
                res[1] = projectName;
                return res;
            }
        }
        return res;
    }

    private List<String> resolveProjectName(List<String> projectFiles) {
        List<String> projects = new ArrayList<>();
        for (String f : projectFiles) {
            SAXReader reader = new SAXReader();
            Document document;
            try {
                document = reader.read(new File(f));
                Element rootElm = document.getRootElement();
                projects.add(rootElm.elementText("name"));
            } catch (DocumentException e) {
                logger.log(Level.WARNING, String.format("Exception on reading project file: %s.", f), e);
            }
        }
        projects.add(null);
        return projects;
    }

    private String resolveMainClassCore(String projectName) throws CoreException {
        IJavaSearchScope searchScope = createSearchScope(projectName);
        SearchPattern pattern = SearchPattern.createPattern("main(String[]) void", IJavaSearchConstants.METHOD,
                IJavaSearchConstants.DECLARATIONS, SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_EXACT_MATCH);
        ArrayList<String> uris = new ArrayList<>();
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                Object element = match.getElement();
                if (element instanceof IMethod) {
                    IMethod method = (IMethod) element;
                    try {
                        if (method.isMainMethod()) {
                            uris.add(method.getDeclaringType().getFullyQualifiedName());
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
        return uris.size() == 0 ? null : uris.get(0);
    }

    private IJavaSearchScope createSearchScope(String projectName) throws CoreException {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        if (projectName == null) {
            return SearchEngine.createWorkspaceScope();
        }
        IProject project = root.getProject(projectName);
        if (!project.exists() || !project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
            return SearchEngine.createWorkspaceScope();
        }

        IJavaProject javaProject = JavaCore.create(project);
        return SearchEngine.createJavaSearchScope(new IJavaProject[] {javaProject},
                IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES);
    }
}
