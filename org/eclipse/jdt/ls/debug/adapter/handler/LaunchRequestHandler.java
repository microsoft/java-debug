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

package org.eclipse.jdt.ls.debug.adapter.handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.ls.debug.DebugUtility;
import org.eclipse.jdt.ls.debug.IDebugSession;
import org.eclipse.jdt.ls.debug.adapter.AdapterUtils;
import org.eclipse.jdt.ls.debug.adapter.Constants;
import org.eclipse.jdt.ls.debug.adapter.ErrorCode;
import org.eclipse.jdt.ls.debug.adapter.Events;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.ISourceLookUpProvider;
import org.eclipse.jdt.ls.debug.adapter.IVirtualMachineManagerProvider;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.ProcessConsole;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.Requests.LaunchArguments;
import org.eclipse.jdt.ls.debug.internal.Logger;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;

public class LaunchRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.LAUNCH);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        LaunchArguments launchArguments = (LaunchArguments) arguments;
        context.setAttached(false);
        context.setSourcePath(launchArguments.sourcePath);

        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);
        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        if (launchArguments.projectName != null) {
            Map<String, Object> options = sourceProvider.getDefaultOptions();
            options.put(Constants.PROJECTNAME, launchArguments.projectName);
            sourceProvider.initialize(options);
        }

        try {
            Logger.logInfo(String.format("Trying to launch Java Program with main class \"%s\", classpath \"%s\".",
                    launchArguments.startupClass, launchArguments.classpath));
            IDebugSession debugSession = DebugUtility.launch(vmProvider.getVirtualMachineManager(),
                    launchArguments.startupClass, launchArguments.classpath);
            context.setDebugSession(debugSession);

            Logger.logInfo("Launching debuggee VM succeeded.");
            ProcessConsole debuggeeConsole = new ProcessConsole(debugSession.process(), "Debuggee");
            debuggeeConsole.onStdout((output) -> {
                // When DA receives a new OutputEvent, it just shows that on Debug Console and doesn't affect the DA's dispatching workflow.
                // That means the debugger can send OutputEvent to DA at any time.
                context.sendEvent(Events.OutputEvent.createStdoutOutput(output));
            });

            debuggeeConsole.onStderr((err) -> {
                context.sendEvent(Events.OutputEvent.createStderrOutput(err));
            });
            debuggeeConsole.start();
        } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
            AdapterUtils.setErrorResponse(response, ErrorCode.LAUNCH_FAILURE,
                    String.format("Failed to launch debuggee VM. Reason: %s", e.toString()));
        }
    }
}
