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

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;

import com.microsoft.java.debug.core.UsageDataStore;

public class JavaDebugDelegateCommandHandler implements IDelegateCommandHandler {

    public static String FETCH_USER_DATA = "vscode.java.fetchUsageData";

    public static String DEBUG_STARTSESSION = "vscode.java.startDebugSession";

    public static String RESOLVE_CLASSPATH = "vscode.java.resolveClasspath";

    public static String RESOLVE_MAINCLASS = "vscode.java.resolveMainClass";

    public static String BUILD_WORKSPACE = "vscode.java.buildWorkspace";

    public static String CONFIG_LOG_LEVEL = "vscode.java.configLogLevel";

    public static String UPDATE_USER_SETTINGS = "vscode.java.updateUserSettings";


    @Override
    public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor progress) throws Exception {
        if (DEBUG_STARTSESSION.equals(commandId)) {
            IDebugServer debugServer = JavaDebugServer.getInstance();
            debugServer.start();
            return debugServer.getPort();
        } else if (RESOLVE_CLASSPATH.equals(commandId)) {
            ResolveClasspathsHandler handler = new ResolveClasspathsHandler();
            return handler.resolveClasspaths(arguments);
        } else if (RESOLVE_MAINCLASS.equals(commandId)) {
            ResolveMainClassHandler handler = new ResolveMainClassHandler();
            return handler.resolveMainClass();
        } else if (BUILD_WORKSPACE.equals(commandId)) {
            // TODO
        } else if (FETCH_USER_DATA.equals(commandId)) {
            return UsageDataStore.getInstance().fetchAll();
        } else if (CONFIG_LOG_LEVEL.equals(commandId)) {
            return LogUtils.configLogLevel(arguments);
        } else if (UPDATE_USER_SETTINGS.equals(commandId)) {
            return UserSettingsUtils.configUserSettings(arguments);
        }

        throw new UnsupportedOperationException(String.format("Java debug plugin doesn't support the command '%s'.", commandId));
    }

}
