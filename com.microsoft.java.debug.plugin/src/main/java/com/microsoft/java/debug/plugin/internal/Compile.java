/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.BuildWorkspaceHandler;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.extended.ProjectBuildParams;

import com.microsoft.java.debug.core.Configuration;

public class Compile {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private static final int GRADLE_BS_COMPILATION_ERROR = 100;

    public static Object compile(CompileParams params, IProgressMonitor monitor) {
        if (params == null) {
            throw new IllegalArgumentException("The compile parameters should not be null.");
        }

        IProject mainProject = JdtUtils.getMainProject(params.getProjectName(), params.getMainClass());
        if (JdtUtils.isBspProject(mainProject) && !ProjectUtils.isGradleProject(mainProject)) {
            // Just need to trigger a build for the target project, the Gradle build server will
            // handle the build dependencies for us.
            try {
                ResourcesPlugin.getWorkspace().build(
                    new IBuildConfiguration[]{mainProject.getActiveBuildConfig()},
                    IncrementalProjectBuilder.INCREMENTAL_BUILD,
                    false /*buildReference*/,
                    monitor
                );
            } catch (CoreException e) {
                if (e.getStatus().getCode() == IResourceStatus.BUILD_FAILED) {
                    return GRADLE_BS_COMPILATION_ERROR;
                } else {
                    return BuildWorkspaceStatus.FAILED;
                }
            }
            return BuildWorkspaceStatus.SUCCEED;
        }

        if (monitor.isCanceled()) {
            return BuildWorkspaceStatus.CANCELLED;
        }

        ProjectBuildParams buildParams = new ProjectBuildParams();
        List<TextDocumentIdentifier> identifiers = new LinkedList<>();
        buildParams.setFullBuild(params.isFullBuild);
        for (IJavaProject javaProject : ProjectUtils.getJavaProjects()) {
            if (ProjectsManager.getDefaultProject().equals(javaProject.getProject())) {
                continue;
            }
            // we only build project which is not a BSP project, in case that the compile request is triggered by
            // HCR with auto-build disabled, the build for BSP projects will be triggered by JavaHotCodeReplaceProvider.
            if (!JdtUtils.isBspProject(javaProject.getProject())) {
                identifiers.add(new TextDocumentIdentifier(javaProject.getProject().getLocationURI().toString()));
            }
        }
        if (identifiers.size() == 0) {
            return BuildWorkspaceStatus.SUCCEED;
        }

        buildParams.setIdentifiers(identifiers);
        long compileAt = System.currentTimeMillis();
        BuildWorkspaceHandler buildWorkspaceHandler = new BuildWorkspaceHandler(JavaLanguageServerPlugin.getProjectsManager());
        BuildWorkspaceStatus status = buildWorkspaceHandler.buildProjects(buildParams, monitor);
        logger.info("Time cost for ECJ: " + (System.currentTimeMillis() - compileAt) + "ms");
        if (status == BuildWorkspaceStatus.FAILED || status == BuildWorkspaceStatus.CANCELLED) {
            return status;
        }

        try {
            IResource currentResource = mainProject;
            if (isUnmanagedFolder(mainProject) && StringUtils.isNotBlank(params.getMainClass())) {
                IType mainType = ProjectUtils.getJavaProject(mainProject).findType(params.getMainClass());
                if (mainType != null && mainType.getResource() != null) {
                    currentResource = mainType.getResource();
                }
            }

            List<IMarker> problemMarkers = new ArrayList<>();
            if (currentResource != null) {
                List<IMarker> markers = ResourceUtils.getErrorMarkers(currentResource);
                if (markers != null) {
                    problemMarkers.addAll(markers);
                }

                // Check if the referenced projects contain compilation errors.
                if (currentResource instanceof IProject && ProjectUtils.isJavaProject((IProject) currentResource)) {
                    IJavaProject currentJavaProject = ProjectUtils.getJavaProject((IProject) currentResource);
                    IJavaProject[] javaProjects = ProjectUtils.getJavaProjects();
                    for (IJavaProject otherJavaProject : javaProjects) {
                        IProject other = otherJavaProject.getProject();
                        if (!other.equals(getDefaultProject()) && !other.equals((IProject) currentResource)
                            && currentJavaProject.isOnClasspath(otherJavaProject)) {
                            markers = ResourceUtils.getErrorMarkers(other);
                            if (markers != null) {
                                problemMarkers.addAll(markers);
                            }
                        }
                    }
                }
            } else {
                IJavaProject[] javaProjects = ProjectUtils.getJavaProjects();
                for (IJavaProject javaProject : javaProjects) {
                    IProject project = javaProject.getProject();
                    if (!project.equals(getDefaultProject())) {
                        List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
                        if (markers != null) {
                            problemMarkers.addAll(markers);
                        }
                    }
                }
            }

            if (!problemMarkers.isEmpty()) {
                return BuildWorkspaceStatus.WITH_ERROR;
            }
        } catch (CoreException e) {
            JavaLanguageServerPlugin.log(e);
        }

        return BuildWorkspaceStatus.SUCCEED;
    }

    private static boolean isUnmanagedFolder(IProject project) {
        return project != null && ProjectUtils.isUnmanagedFolder(project)
            && ProjectUtils.isJavaProject(project);
    }

    private static IProject getDefaultProject() {
        return getWorkspaceRoot().getProject(ProjectsManager.DEFAULT_PROJECT_NAME);
    }

    private static IWorkspaceRoot getWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    class CompileParams {
        String projectName;
        String mainClass;
        boolean isFullBuild = false;

        public String getMainClass() {
            return mainClass;
        }

        public boolean isFullBuild() {
            return isFullBuild;
        }

        public String getProjectName() {
            return projectName;
        }
    }
}
