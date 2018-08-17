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

package com.microsoft.java.debug.core.adapter.handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.google.gson.JsonObject;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.DebugSession;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.ICompletionsProvider;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Messages.Request;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.CONSOLE;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.LaunchArguments;
import com.microsoft.java.debug.core.protocol.Requests.RunInTerminalRequestArguments;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.VMStartException;

public class LaunchRequestHandler extends AbstractLaunchRequestHandler {

    private static final int ACCEPT_TIMEOUT = 10 * 1000;
    private static final String TERMINAL_TITLE = "Java Debug Console";

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.LAUNCH);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        return handleLaunchCommand(arguments, response, context);
    }

    @Override
    protected CompletableFuture<Response> launchInTerminal(LaunchArguments launchArguments, Response response, IDebugAdapterContext context) {
        CompletableFuture<Response> resultFuture = new CompletableFuture<>();

        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);
        final String launchInTerminalErrorFormat = "Failed to launch debuggee in terminal. Reason: %s";

        try {
            List<ListeningConnector> connectors = vmProvider.getVirtualMachineManager().listeningConnectors();
            ListeningConnector listenConnector = connectors.get(0);
            Map<String, Connector.Argument> args = listenConnector.defaultArguments();
            ((Connector.IntegerArgument) args.get("timeout")).setValue(ACCEPT_TIMEOUT);
            String address = listenConnector.startListening(args);

            String[] cmds = constructLaunchCommands(launchArguments, false, address);
            RunInTerminalRequestArguments requestArgs = null;
            if (launchArguments.console == CONSOLE.integratedTerminal) {
                requestArgs = RunInTerminalRequestArguments.createIntegratedTerminal(
                        cmds,
                        launchArguments.cwd,
                        launchArguments.env,
                        TERMINAL_TITLE);
            } else {
                requestArgs = RunInTerminalRequestArguments.createExternalTerminal(
                        cmds,
                        launchArguments.cwd,
                        launchArguments.env,
                        TERMINAL_TITLE);
            }
            Request request = new Request(Command.RUNINTERMINAL.getName(),
                    (JsonObject) JsonUtils.toJsonTree(requestArgs, RunInTerminalRequestArguments.class));

            // Notes: In windows (reference to https://support.microsoft.com/en-us/help/830473/command-prompt-cmd--exe-command-line-string-limitation),
            // when launching the program in cmd.exe, if the command line length exceed the threshold value (8191 characters),
            // it will be automatically truncated so that launching in terminal failed. Especially, for maven project, the class path contains
            // the local .m2 repository path, it may exceed the limit.
            context.getProtocolServer().sendRequest(request, RUNINTERMINAL_TIMEOUT)
                .whenComplete((runResponse, ex) -> {
                    if (runResponse != null) {
                        if (runResponse.success) {
                            try {
                                VirtualMachine vm = listenConnector.accept(args);
                                context.setDebugSession(new DebugSession(vm));
                                logger.info("Launching debuggee in terminal console succeeded.");
                                resultFuture.complete(response);
                            } catch (IOException | IllegalConnectorArgumentsException e) {
                                resultFuture.completeExceptionally(
                                        new DebugException(
                                                String.format(launchInTerminalErrorFormat, e.toString()),
                                                ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()
                                        )
                                );
                            }
                        } else {
                            resultFuture.completeExceptionally(
                                    new DebugException(
                                            String.format(launchInTerminalErrorFormat, runResponse.message),
                                            ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()
                                    )
                            );
                        }
                    } else {
                        if (ex instanceof CompletionException && ex.getCause() != null) {
                            ex = ex.getCause();
                        }
                        String errorMessage = String.format(launchInTerminalErrorFormat, ex != null ? ex.toString() : "Null response");
                        resultFuture.completeExceptionally(
                                new DebugException(
                                        String.format(launchInTerminalErrorFormat, errorMessage),
                                        ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()
                                )
                        );
                    }
                });
        } catch (IOException | IllegalConnectorArgumentsException e) {
            resultFuture.completeExceptionally(
                    new DebugException(
                            String.format(launchInTerminalErrorFormat, e.toString()),
                            ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()
                    )
            );
        }

        return resultFuture;
    }

    @Override
    protected Process launchInternalDebuggeeProcess(LaunchArguments launchArguments, IDebugAdapterContext context)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);

        IDebugSession debugSession = DebugUtility.launch(
                vmProvider.getVirtualMachineManager(),
                launchArguments.mainClass,
                launchArguments.args,
                launchArguments.vmArgs,
                Arrays.asList(launchArguments.modulePaths),
                Arrays.asList(launchArguments.classPaths),
                launchArguments.cwd,
                constructEnvironmentVaraiables(launchArguments));
        context.setDebugSession(debugSession);

        logger.info("Launching debuggee VM succeeded.");
        return debugSession.process();
    }

    @Override
    protected void postLaunchConfiguration(LaunchArguments launchArguments, IDebugAdapterContext context) {
        Map<String, Object> options = new HashMap<>();
        options.put(Constants.DEBUGGEE_ENCODING, context.getDebuggeeEncoding());
        if (launchArguments.projectName != null) {
            options.put(Constants.PROJECT_NAME, launchArguments.projectName);
        }
        if (launchArguments.mainClass != null) {
            options.put(Constants.MAIN_CLASS, launchArguments.mainClass);
        }

        // TODO: Clean up the initialize mechanism
        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        sourceProvider.initialize(context, options);
        IEvaluationProvider evaluationProvider = context.getProvider(IEvaluationProvider.class);
        evaluationProvider.initialize(context, options);
        IHotCodeReplaceProvider hcrProvider = context.getProvider(IHotCodeReplaceProvider.class);
        hcrProvider.initialize(context, options);
        ICompletionsProvider completionsProvider = context.getProvider(ICompletionsProvider.class);
        completionsProvider.initialize(context, options);

        // send an InitializedEvent to indicate that the debugger is ready to accept
        // configuration requests (e.g. SetBreakpointsRequest, SetExceptionBreakpointsRequest).
        context.getProtocolServer().sendEvent(new Events.InitializedEvent());
    }
}
