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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.UsageDataStore;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Requests.LaunchArguments;
import com.microsoft.java.debug.plugin.internal.InlineValueHandler.InlineParams;

public class JavaDebugDelegateCommandHandler implements IDelegateCommandHandler {
    public static final String FETCH_USER_DATA = "vscode.java.fetchUsageData";
    public static final String DEBUG_STARTSESSION = "vscode.java.startDebugSession";
    public static final String RESOLVE_CLASSPATH = "vscode.java.resolveClasspath";
    public static final String RESOLVE_MAINCLASS = "vscode.java.resolveMainClass";
    public static final String BUILD_WORKSPACE = "vscode.java.buildWorkspace";
    public static final String UPDATE_DEBUG_SETTINGS = "vscode.java.updateDebugSettings";
    public static final String VALIDATE_LAUNCHCONFIG = "vscode.java.validateLaunchConfig";
    public static final String RESOLVE_MAINMETHOD = "vscode.java.resolveMainMethod";
    public static final String INFER_LAUNCH_COMMAND_LENGTH = "vscode.java.inferLaunchCommandLength";
    public static final String CHECK_PROJECT_SETTINGS = "vscode.java.checkProjectSettings";
    public static final String RESOLVE_ELEMENT_AT_SELECTION = "vscode.java.resolveElementAtSelection";
    public static final String RESOLVE_BUILD_FILES = "vscode.java.resolveBuildFiles";
    public static final String IS_ON_CLASSPATH = "vscode.java.isOnClasspath";
    public static final String RESOLVE_JAVA_EXECUTABLE = "vscode.java.resolveJavaExecutable";
    public static final String FETCH_PLATFORM_SETTINGS = "vscode.java.fetchPlatformSettings";
    public static final String RESOLVE_CLASSFILTERS = "vscode.java.resolveClassFilters";
    public static final String RESOLVE_SOURCEURI = "vscode.java.resolveSourceUri";
    public static final String RESOLVE_INLINEVARIABLES = "vscode.java.resolveInlineVariables";

    @Override
    public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor progress) throws Exception {
        switch (commandId) {
            case DEBUG_STARTSESSION:
                IDebugServer debugServer = JavaDebugServer.getInstance();
                debugServer.start();
                return debugServer.getPort();
            case RESOLVE_CLASSPATH:
                ResolveClasspathsHandler handler = new ResolveClasspathsHandler();
                return handler.resolveClasspaths(arguments);
            case RESOLVE_MAINCLASS:
                ResolveMainClassHandler resolveMainClassHandler = new ResolveMainClassHandler();
                return resolveMainClassHandler.resolveMainClass(arguments);
            case BUILD_WORKSPACE:
                // TODO
                break;
            case FETCH_USER_DATA:
                return UsageDataStore.getInstance().fetchAll();
            case UPDATE_DEBUG_SETTINGS:
                return DebugSettingUtils.configDebugSettings(arguments);
            case VALIDATE_LAUNCHCONFIG:
                return new ResolveMainClassHandler().validateLaunchConfig(arguments);
            case RESOLVE_MAINMETHOD:
                return ResolveMainMethodHandler.resolveMainMethods(arguments, progress);
            case INFER_LAUNCH_COMMAND_LENGTH:
                return LaunchCommandHandler.getLaunchCommandLength(JsonUtils.fromJson((String) arguments.get(0), LaunchArguments.class));
            case CHECK_PROJECT_SETTINGS:
                return ProjectSettingsChecker.check(JsonUtils.fromJson((String) arguments.get(0), ProjectSettingsChecker.ProjectSettingsCheckerParams.class));
            case RESOLVE_ELEMENT_AT_SELECTION:
                return ResolveElementHandler.resolveElementAtSelection(arguments, progress);
            case RESOLVE_BUILD_FILES:
                return getBuildFiles();
            case IS_ON_CLASSPATH:
                return isOnClasspath(arguments);
            case RESOLVE_JAVA_EXECUTABLE:
                return ResolveJavaExecutableHandler.resolveJavaExecutable(arguments);
            case FETCH_PLATFORM_SETTINGS:
                return PlatformSettings.getPlatformSettings();
            case RESOLVE_CLASSFILTERS:
                return JavaClassFilter.resolveClassFilters(arguments);
            case RESOLVE_SOURCEURI:
                return ResolveSourceMappingHandler.resolveSourceUri(arguments);
            case RESOLVE_INLINEVARIABLES:
                return InlineValueHandler.resolveInlineVariables(JsonUtils.fromJson((String) arguments.get(0), InlineParams.class), progress);
            default:
                break;
        }

        throw new UnsupportedOperationException(String.format("Java debug plugin doesn't support the command '%s'.", commandId));
    }

    private List<String> getBuildFiles() {
        List<String> result = new ArrayList<>();
        List<IJavaProject> javaProjects = JdtUtils.listJavaProjects(ResourcesPlugin.getWorkspace().getRoot());
        for (IJavaProject javaProject : javaProjects) {
            IFile buildFile = null;
            if (ProjectUtils.isMavenProject(javaProject.getProject())) {
                buildFile = javaProject.getProject().getFile("pom.xml");
            } else if (ProjectUtils.isGradleProject(javaProject.getProject())) {
                buildFile = javaProject.getProject().getFile("build.gradle");
            }

            if (buildFile != null && buildFile.exists() && buildFile.getLocationURI() != null) {
                result.add(ResourceUtils.fixURI(buildFile.getLocationURI()));
            }
        }

        return result;
    }

    private boolean isOnClasspath(List<Object> arguments) throws DebugException {
        if (arguments.size() < 1) {
            throw new DebugException("No file uri is specified.");
        }

        String uri = (String) arguments.get(0);
        final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
        if (unit == null || unit.getResource() == null || !unit.getResource().exists()) {
            throw new DebugException("The compilation unit " + uri + " doesn't exist.");
        }

        IJavaProject javaProject = unit.getJavaProject();
        return javaProject == null || javaProject.isOnClasspath(unit);
    }
}
