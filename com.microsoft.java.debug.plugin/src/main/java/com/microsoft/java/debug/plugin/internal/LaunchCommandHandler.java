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

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.adapter.handler.LaunchRequestHandler;
import com.microsoft.java.debug.core.protocol.Requests.CONSOLE;
import com.microsoft.java.debug.core.protocol.Requests.LaunchArguments;

public class LaunchCommandHandler {

    /**
     * Get the approximate command line length based on the launch arguments.
     * @param launchArguments - the launch arguments
     * @return the approximate command line length
     */
    public static int getLaunchCommandLength(LaunchArguments launchArguments) {
        String encoding = StringUtils.isBlank(launchArguments.encoding) ? StandardCharsets.UTF_8.name() : launchArguments.encoding;
        launchArguments.vmArgs += String.format(" -Dfile.encoding=%s", encoding);
        String address = launchArguments.noDebug ? "" : "888888";
        String[] commands = LaunchRequestHandler.constructLaunchCommands(launchArguments, false, address);
        int cwdLength = launchArguments.console == CONSOLE.internalConsole ? 0 : StringUtils.length("cd " + launchArguments.cwd + " && ");
        return cwdLength + String.join(" ", commands).length();
    }

}
