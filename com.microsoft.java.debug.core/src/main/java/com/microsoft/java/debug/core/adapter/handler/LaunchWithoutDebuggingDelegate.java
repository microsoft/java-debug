/*******************************************************************************
* Copyright (c) 2018-2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Messages.Request;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.CONSOLE;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.LaunchArguments;
import com.microsoft.java.debug.core.protocol.Requests.RunInTerminalRequestArguments;
import com.microsoft.java.debug.core.protocol.Responses.RunInTerminalResponseBody;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;

public class LaunchWithoutDebuggingDelegate implements ILaunchDelegate {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    protected static final long RUNINTERMINAL_TIMEOUT = 10 * 1000;
    private Consumer<IDebugAdapterContext> terminateHandler;

    public LaunchWithoutDebuggingDelegate(Consumer<IDebugAdapterContext> terminateHandler) {
        this.terminateHandler = terminateHandler;
    }

    @Override
    public Process launch(LaunchArguments launchArguments, IDebugAdapterContext context)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        String[] cmds = LaunchRequestHandler.constructLaunchCommands(launchArguments, false, null);
        File workingDir = null;
        if (launchArguments.cwd != null && Files.isDirectory(Paths.get(launchArguments.cwd))) {
            workingDir = new File(launchArguments.cwd);
        }
        Process debuggeeProcess = Runtime.getRuntime().exec(cmds, LaunchRequestHandler.constructEnvironmentVariables(launchArguments),
                workingDir);
        new Thread() {
            public void run() {
                try {
                    debuggeeProcess.waitFor();
                } catch (InterruptedException ignore) {
                    logger.warning(String.format("Current thread is interrupted. Reason: %s", ignore.toString()));
                    debuggeeProcess.destroy();
                } finally {
                    terminateHandler.accept(context);
                }
            }
        }.start();
        logger.info("Launching debuggee proccess succeeded.");
        return debuggeeProcess;
    }

    @Override
    public void postLaunch(LaunchArguments launchArguments, IDebugAdapterContext context) {
        // For NO_DEBUG launch mode, the debugger does not respond to requests like
        // SetBreakpointsRequest,
        // but the front end keeps sending them according to the Debug Adapter Protocol.
        // To avoid receiving them, a workaround is not to send InitializedEvent back to
        // the front end.
        // See https://github.com/Microsoft/vscode/issues/55850#issuecomment-412819676
        return;
    }

    @Override
    public CompletableFuture<Response> launchInTerminal(LaunchArguments launchArguments, Response response,
            IDebugAdapterContext context) {
        CompletableFuture<Response> resultFuture = new CompletableFuture<>();

        final String launchInTerminalErrorFormat = "Failed to launch debuggee in terminal. Reason: %s";

        final String[] names = launchArguments.mainClass.split("[/\\.]");
        final String terminalName = "Run: " + names[names.length - 1];
        String[] cmds = LaunchRequestHandler.constructLaunchCommands(launchArguments, false, null);
        RunInTerminalRequestArguments requestArgs = null;
        if (launchArguments.console == CONSOLE.integratedTerminal) {
            requestArgs = RunInTerminalRequestArguments.createIntegratedTerminal(cmds, launchArguments.cwd,
                    launchArguments.env, terminalName);
        } else {
            requestArgs = RunInTerminalRequestArguments.createExternalTerminal(cmds, launchArguments.cwd,
                    launchArguments.env, terminalName);
        }
        Request request = new Request(Command.RUNINTERMINAL.getName(),
                (JsonObject) JsonUtils.toJsonTree(requestArgs, RunInTerminalRequestArguments.class));

        // Notes: In windows (reference to
        // https://support.microsoft.com/en-us/help/830473/command-prompt-cmd--exe-command-line-string-limitation),
        // when launching the program in cmd.exe, if the command line length exceed the
        // threshold value (8191 characters),
        // it will be automatically truncated so that launching in terminal failed.
        // Especially, for maven project, the class path contains
        // the local .m2 repository path, it may exceed the limit.
        context.getProtocolServer().sendRequest(request, RUNINTERMINAL_TIMEOUT).whenComplete((runResponse, ex) -> {
            if (runResponse != null) {
                if (runResponse.success) {
                    ProcessHandle debuggeeProcess = null;
                    try {
                        RunInTerminalResponseBody terminalResponse = JsonUtils.fromJson(
                            JsonUtils.toJson(runResponse.body), RunInTerminalResponseBody.class);
                        context.setProcessId(terminalResponse.processId);
                        context.setShellProcessId(terminalResponse.shellProcessId);

                        if (terminalResponse.processId > 0) {
                            debuggeeProcess = ProcessHandle.of(terminalResponse.processId).orElse(null);
                        } else if (terminalResponse.shellProcessId > 0) {
                            debuggeeProcess = LaunchUtils.findJavaProcessInTerminalShell(terminalResponse.shellProcessId, cmds[0], 3000);
                        }

                        if (debuggeeProcess != null) {
                            context.setProcessId(debuggeeProcess.pid());
                            debuggeeProcess.onExit().thenAcceptAsync(proc -> {
                                context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
                            });
                        }
                    } catch (JsonSyntaxException e) {
                        logger.severe("Failed to resolve runInTerminal response: " + e.toString());
                    }

                    if (debuggeeProcess == null || !debuggeeProcess.isAlive()) {
                        context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
                    }
                    resultFuture.complete(response);
                } else {
                    resultFuture.completeExceptionally(
                            new DebugException(String.format(launchInTerminalErrorFormat, runResponse.message),
                                    ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()));
                }
            } else {
                if (ex instanceof CompletionException && ex.getCause() != null) {
                    ex = ex.getCause();
                }
                String errorMessage = String.format(launchInTerminalErrorFormat,
                        ex != null ? ex.toString() : "Null response");
                resultFuture.completeExceptionally(
                        new DebugException(String.format(launchInTerminalErrorFormat, errorMessage),
                                ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()));
            }
        });
        return resultFuture;
    }

    @Override
    public void preLaunch(LaunchArguments launchArguments, IDebugAdapterContext context) {
        context.setSourcePaths(launchArguments.sourcePaths);
    }
}
