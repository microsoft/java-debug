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
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.microsoft.java.debug.core.Configuration;
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
import com.microsoft.java.debug.core.protocol.Requests.CONSOLE;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.LaunchArguments;
import com.microsoft.java.debug.core.protocol.Requests.RunInTerminalRequestArguments;
import com.microsoft.java.debug.core.protocol.Responses.RunInTerminalResponseBody;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.TransportTimeoutException;
import com.sun.jdi.connect.VMStartException;

public class LaunchWithDebuggingDelegate implements ILaunchDelegate {

    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final int ATTACH_TERMINAL_TIMEOUT = 20 * 1000;
    private static final String TERMINAL_TITLE = "Java Debug Console";
    protected static final long RUNINTERMINAL_TIMEOUT = 10 * 1000;
    private VMHandler vmHandler = new VMHandler();

    @Override
    public CompletableFuture<Response> launchInTerminal(LaunchArguments launchArguments, Response response, IDebugAdapterContext context) {
        CompletableFuture<Response> resultFuture = new CompletableFuture<>();

        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);
        vmHandler.setVmProvider(vmProvider);
        final String launchInTerminalErrorFormat = "Failed to launch debuggee in terminal. Reason: %s";

        try {
            List<ListeningConnector> connectors = vmProvider.getVirtualMachineManager().listeningConnectors();
            ListeningConnector listenConnector = connectors.get(0);
            Map<String, Connector.Argument> args = listenConnector.defaultArguments();
            ((Connector.IntegerArgument) args.get("timeout")).setValue(ATTACH_TERMINAL_TIMEOUT);
            String address = listenConnector.startListening(args);

            String[] cmds = LaunchRequestHandler.constructLaunchCommands(launchArguments, false, address);
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
                                try {
                                    RunInTerminalResponseBody terminalResponse = JsonUtils.fromJson(
                                        JsonUtils.toJson(runResponse.body), RunInTerminalResponseBody.class);
                                    context.setProcessId(terminalResponse.processId);
                                    context.setShellProcessId(terminalResponse.shellProcessId);
                                } catch (JsonSyntaxException e) {
                                    logger.severe("Failed to resolve runInTerminal response: " + e.toString());
                                }
                                VirtualMachine vm = listenConnector.accept(args);
                                vmHandler.connectVirtualMachine(vm);
                                context.setDebugSession(new DebugSession(vm));
                                logger.info("Launching debuggee in terminal console succeeded.");
                                resultFuture.complete(response);
                            } catch (TransportTimeoutException e) {
                                int commandLength = StringUtils.length(launchArguments.cwd) + 1;
                                for (String cmd : cmds) {
                                    commandLength += StringUtils.length(cmd) + 1;
                                }

                                final int threshold = SystemUtils.IS_OS_WINDOWS ? 8092 : 32 * 1024;
                                String errorMessage = String.format(launchInTerminalErrorFormat, e.toString());
                                if (commandLength >= threshold) {
                                    errorMessage = "Failed to launch debuggee in terminal. The possible reason is the command line too long. "
                                            + "More details: " + e.toString();
                                    logger.severe(errorMessage
                                            + "\r\n"
                                            + "The estimated command line length is " + commandLength + ". "
                                            + "Try to enable shortenCommandLine option in the debug launch configuration.");
                                }

                                resultFuture.completeExceptionally(
                                        new DebugException(
                                                errorMessage,
                                                ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()
                                        )
                                );
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
    public Process launch(LaunchArguments launchArguments, IDebugAdapterContext context)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);
        vmHandler.setVmProvider(vmProvider);

        IDebugSession debugSession = DebugUtility.launch(
                vmProvider.getVirtualMachineManager(),
                launchArguments.mainClass,
                launchArguments.args,
                launchArguments.vmArgs,
                Arrays.asList(launchArguments.modulePaths),
                Arrays.asList(launchArguments.classPaths),
                launchArguments.cwd,
                LaunchRequestHandler.constructEnvironmentVariables(launchArguments),
                launchArguments.javaExec);
        context.setDebugSession(debugSession);
        vmHandler.connectVirtualMachine(debugSession.getVM());

        logger.info("Launching debuggee VM succeeded.");
        return debugSession.process();
    }

    @Override
    public void postLaunch(LaunchArguments launchArguments, IDebugAdapterContext context) {
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

    @Override
    public void preLaunch(LaunchArguments launchArguments, IDebugAdapterContext context) {
        // debug only
        context.setAttached(false);
        context.setSourcePaths(launchArguments.sourcePaths);
        context.setVmStopOnEntry(launchArguments.stopOnEntry);
        context.setMainClass(LaunchRequestHandler.parseMainClassWithoutModuleName(launchArguments.mainClass));
        context.setStepFilters(launchArguments.stepFilters);
    }
}
