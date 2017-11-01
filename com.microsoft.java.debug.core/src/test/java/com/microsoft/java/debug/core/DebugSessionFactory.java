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

package com.microsoft.java.debug.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.event.VMDisconnectEvent;

public class DebugSessionFactory {
    private static final String TEST_ROOT = "../com.microsoft.java.debug.test/project";
    private static String rootPath = new File(TEST_ROOT).getAbsolutePath();
    private static Set<String> readyForLaunchProjects = new HashSet<>();
    private static Map<String, IDebugSession> debugSessionMap = Collections.synchronizedMap(new HashMap<>());

    protected static void setupProject(String projectName) throws Exception {
        if (readyForLaunchProjects.contains(projectName)) {
            return;
        }
        String projectRoot = new File(rootPath, projectName).getAbsolutePath();

        List<String> javaSources = new ArrayList<>();
        for (File javaFile : FileUtils.listFiles(new File(projectRoot, "src"), new String[] { "java" }, true)) {
            javaSources.add(javaFile.getAbsolutePath());
        }
        CompileUtils.compileFiles(new File(projectRoot), javaSources);
        readyForLaunchProjects.add(projectName);
    }

    public static IDebugSession getDebugSession(String projectName, String mainClass) throws Exception {
        setupProject(projectName);
        return debugSessionMap.computeIfAbsent(projectName, (name) -> {
            String projectRoot = new File(rootPath, name).getAbsolutePath();
            try {
                final IDebugSession debugSession = DebugUtility.launch(Bootstrap.virtualMachineManager(), mainClass, "", "",
                        new File(projectRoot, "bin").getAbsolutePath(), null, null);
                debugSession.eventHub().events().subscribe(debugEvent -> {
                    if (debugEvent.event instanceof VMDisconnectEvent) {
                        try {
                            debugSession.eventHub().close();
                        } catch (Exception e) {
                            // do nothing.
                        }
                    }
                });
                return debugSession;
            } catch (Exception ex) {
                return null;
            }
        });
    }

    public static void shutdownDebugSession(String projectName) throws Exception {
        try {
            IDebugSession debugSession = debugSessionMap.remove(projectName);
            if (debugSession != null) {
                System.out.println("Shutdown debug session.");
                debugSession.terminate();
            }
        } catch (VMDisconnectedException ex) {
            // ignore
        }
    }

}
