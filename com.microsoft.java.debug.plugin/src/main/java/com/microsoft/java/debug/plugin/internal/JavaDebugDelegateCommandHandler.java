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

import java.nio.charset.Charset;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;

import com.microsoft.java.debug.core.UsageDataStore;

public class JavaDebugDelegateCommandHandler implements IDelegateCommandHandler {

    public static String FETCH_USER_DATA = "vscode.java.fetchUsageData";

    public static String DEBUG_STARTSESSION = "vscode.java.startDebugSession";

    public static String RESOLVE_CLASSPATH = "vscode.java.resolveClasspath";

    public static String RESOLVE_MAINCLASS = "vscode.java.resolveMainClass";

    public static String BUILD_WORKSPACE = "vscode.java.buildWorkspace";

    public static String UPDATE_DEBUG_SETTINGS = "vscode.java.updateDebugSettings";

    public static String START_REMOTE_DEBUG_SESSION = "vscode.java.startRemoteDebugSession";

    public static String STOP_REMOTE_DEBUG_SESSION = "vscode.java.stopRemoteDebugSession";

    public static String RESOLVE_CLASS_NAME = "vscode.java.resolveClassNameForRemoteDebug";

    public static String RESOLVE_SOURCE_NAME = "vscode.java.resolveSourceNameForRemoteDebug";

    public static String OK = "ok";

    private static RemoteDebugHandler remoteDebugHandler;

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
            return handler.resolveMainClass(arguments);
        } else if (BUILD_WORKSPACE.equals(commandId)) {
            // TODO
        } else if (FETCH_USER_DATA.equals(commandId)) {
            return UsageDataStore.getInstance().fetchAll();
        } else if (UPDATE_DEBUG_SETTINGS.equals(commandId)) {
            return DebugSettingUtils.configDebugSettings(arguments);
        } else if (RESOLVE_CLASS_NAME.equals(commandId)) {
            String file = (String) arguments.get(0);
            int[] intArrayOfLines = new int[arguments.size() - 1];
            for (int i = 1; i < intArrayOfLines.length; i++) {
                intArrayOfLines[i - 1] = Integer.valueOf((String) arguments.get(i));
            }
            return StringUtils.join(remoteDebugHandler.resolveClassName(file, intArrayOfLines, null), ";");
        } else if (RESOLVE_SOURCE_NAME.equals(commandId)) {
            String fqn = (String) arguments.get(0);
            String sourcePath = (String) arguments.get(1);
            return remoteDebugHandler.resolveSourceName(fqn, sourcePath);
        } else if (START_REMOTE_DEBUG_SESSION.equals(commandId)) {
            String projectName = (String) arguments.get(0);
            String charsetName = (String) arguments.get(1);
            remoteDebugHandler = new RemoteDebugHandler(projectName, StringUtils.isBlank(charsetName)
                    ? Charset.defaultCharset() : Charset.forName(charsetName));
            return OK;
        } else if (STOP_REMOTE_DEBUG_SESSION.equals(commandId)) {
            remoteDebugHandler = null;
            return OK;
        }
        throw new UnsupportedOperationException(String.format("Java debug plugin doesn't support the command '%s'.", commandId));
    }

}
