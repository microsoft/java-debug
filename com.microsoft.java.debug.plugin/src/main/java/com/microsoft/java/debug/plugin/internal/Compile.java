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
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

import com.microsoft.java.debug.core.Configuration;

public class Compile {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private static final int GRADLE_BS_COMPILATION_ERROR = 100;

    public static Object compile(CompileParams params, IProgressMonitor monitor) {
        IProject mainProject = params == null ? null : ProjectUtils.getProject(params.getProjectName());
        if (mainProject == null) {
            try {
                // Q: is infer project by main class name necessary? perf impact?
                List<IJavaProject> javaProjects = ResolveClasspathsHandler.getJavaProjectFromType(params.getMainClass());
                if (javaProjects.size() == 1) {
                    mainProject = javaProjects.get(0).getProject();
                }
            } catch (CoreException e) {
                JavaLanguageServerPlugin.logException("Failed to resolve project from main class name.", e);
            }
        }

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

        try {
            if (monitor.isCanceled()) {
                return BuildWorkspaceStatus.CANCELLED;
            }

            long compileAt = System.currentTimeMillis();
            if (params != null && params.isFullBuild()) {
                ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
                ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
            } else {
                ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
            }
            logger.info("Time cost for ECJ: " + (System.currentTimeMillis() - compileAt) + "ms");

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

            if (problemMarkers.isEmpty()) {
                return BuildWorkspaceStatus.SUCCEED;
            }

            return BuildWorkspaceStatus.WITH_ERROR;
        } catch (CoreException e) {
            JavaLanguageServerPlugin.logException("Failed to build workspace.", e);
            return BuildWorkspaceStatus.FAILED;
        } catch (OperationCanceledException e) {
            return BuildWorkspaceStatus.CANCELLED;
        }
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
