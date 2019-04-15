/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;

public class ProjectSettingsChecker {
    /**
     * Check whether the project settings are all matched as the expected.
     * @param params - The checker parameters
     * @return true if all settings is matched
     */
    public static boolean check(ProjectSettingsCheckerParams params) {
        Map<String, String> options = new HashMap<>();
        IJavaProject javaProject = null;
        if (StringUtils.isNotBlank(params.projectName)) {
            javaProject = JdtUtils.getJavaProject(params.projectName);
        } else if (StringUtils.isNotBlank(params.className)) {
            try {
                List<IJavaProject> projects = ResolveClasspathsHandler.getJavaProjectFromType(params.className);
                if (!projects.isEmpty()) {
                    javaProject = projects.get(0);
                }
            } catch (CoreException e) {
                // do nothing
            }
        }

        if (javaProject != null) {
            options = javaProject.getOptions(params.inheritedOptions);
        }

        for (Entry<String, String> expected : params.expectedOptions.entrySet()) {
            if (!Objects.equals(options.get(expected.getKey()), expected.getValue())) {
                return false;
            }
        }

        return true;
    }

    public static class ProjectSettingsCheckerParams {
        String className;
        String projectName;
        boolean inheritedOptions;
        Map<String, String> expectedOptions;
    }
}
