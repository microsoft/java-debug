/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

import com.microsoft.java.debug.core.Configuration;

public class ResolveJavaExecutableHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final String[] javaExecCandidates = {
        "java",
        "java.exe",
        "javaw",
        "javaw.exe",
        "j9",
        "j9.exe",
        "j9w",
        "j9w.exe"
    };
    private static final String[] javaBinCandidates = {
        File.separator,
        "bin" + File.separatorChar,
        "jre" + File.separatorChar + "bin" + File.separatorChar
    };

    /**
     * Resolve the java executable path from the project's java runtime.
     */
    public static String resolveJavaExecutable(List<Object> arguments) throws Exception {
        try {
            String mainClass = (String) arguments.get(0);
            String projectName = (String) arguments.get(1);
            IJavaProject targetProject = null;
            if (StringUtils.isNotBlank(projectName)) {
                targetProject = JdtUtils.getJavaProject(projectName);
            } else {
                List<IJavaProject> targetProjects = ResolveClasspathsHandler.getJavaProjectFromType(mainClass);
                if (!targetProjects.isEmpty()) {
                    targetProject = targetProjects.get(0);
                }
            }

            return resolveJavaExecutable(targetProject);
        } catch (CoreException e) {
            logger.log(Level.SEVERE, "Failed to resolve java executable: " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Resolve the Java executable path from the project's Java runtime.
     */
    public static String resolveJavaExecutable(IJavaProject javaProject) throws CoreException {
        if (javaProject == null) {
            return null;
        }

        IVMInstall vmInstall = JavaRuntime.getVMInstall(javaProject);
        if (vmInstall == null || vmInstall.getInstallLocation() == null) {
            return null;
        }

        File exe = findJavaExecutable(vmInstall.getInstallLocation());
        if (exe == null) {
            return null;
        }

        return exe.getAbsolutePath();
    }

    private static File findJavaExecutable(File vmInstallLocation) {
        boolean isBin = Objects.equals("bin", vmInstallLocation.getName());
        for (int i = 0; i < javaExecCandidates.length; i++) {
            for (int j = 0; j < javaBinCandidates.length; j++) {
                if (!isBin && j == 0) {
                    continue;
                }

                File javaFile = new File(vmInstallLocation, javaBinCandidates[j] + javaExecCandidates[i]);
                if (javaFile.isFile()) {
                    return javaFile;
                }
            }
        }

        return null;
    }
}
