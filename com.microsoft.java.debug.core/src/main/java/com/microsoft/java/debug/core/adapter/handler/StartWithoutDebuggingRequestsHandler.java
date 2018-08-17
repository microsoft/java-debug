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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.google.gson.JsonObject;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.LaunchMode;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Messages.Request;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.CONSOLE;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.DisconnectArguments;
import com.microsoft.java.debug.core.protocol.Requests.LaunchArguments;
import com.microsoft.java.debug.core.protocol.Requests.RunInTerminalRequestArguments;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;

public class StartWithoutDebuggingRequestsHandler extends AbstractLaunchRequestHandler {
    private static final String TERMINAL_TITLE = "Java Process Console";
    private Process debuggeeProcess;

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.values());
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        if (Command.INITIALIZE.equals(command)) {
            return CompletableFuture.completedFuture(response);
        } else if (Command.LAUNCH.equals(command)) {
            LaunchArguments launchArguments = (LaunchArguments) arguments;
            return launchArguments.noDebug ? handleLaunchCommand(arguments, response, context) : CompletableFuture.completedFuture(response);
        } else if (Command.DISCONNECT.equals(command)) {
            return handleDisconnectCommand(arguments, response, context);
        } else {
            if (context.getLaunchMode() == LaunchMode.NO_DEBUG) {
                String errorMsg = String.format("Unrecognized request: { _request: %s }", command);
                throw AdapterUtils.createCompletionException(errorMsg, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE);
            }
            return CompletableFuture.completedFuture(response);
        }
    }

    @Override
    protected Process launchInternalDebuggeeProcess(LaunchArguments launchArguments, IDebugAdapterContext context)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        String[] cmds = constructLaunchCommands(launchArguments, false, null);
        File workingDir = null;
        if (launchArguments.cwd != null && Files.isDirectory(Paths.get(launchArguments.cwd))) {
            workingDir = new File(launchArguments.cwd);
        }
        this.debuggeeProcess = Runtime.getRuntime().exec(cmds, constructEnvironmentVaraiables(launchArguments),
                workingDir);
        new Thread() {
            public void run() {
                try {
                    debuggeeProcess.waitFor();
                } catch (InterruptedException ignore) {
                    logger.warning(String.format("Current thread is interrupted. Reason: %s", ignore.toString()));
                    debuggeeProcess.destroy();
                } finally {
                    context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
                }
            }
        }.start();
        logger.info("Launching debuggee proccess succeeded.");
        return this.debuggeeProcess;
    }

    @Override
    protected void postLaunchConfiguration(LaunchArguments launchArguments, IDebugAdapterContext context) {
        // For NO_DEBUG launch mode, the debugger does not respond to requests like
        // SetBreakpointsRequest,
        // but the front end keeps sending them according to the Debug Adapter Protocol.
        // To avoid receiving them, a workaround is not to send InitializedEvent back to
        // the front end.
        // See https://github.com/Microsoft/vscode/issues/55850#issuecomment-412819676
        return;
    }

    @Override
    protected CompletableFuture<Response> launchInTerminal(LaunchArguments launchArguments, Response response,
            IDebugAdapterContext context) {
        CompletableFuture<Response> resultFuture = new CompletableFuture<>();

        final String launchInTerminalErrorFormat = "Failed to launch debuggee in terminal. Reason: %s";

        String[] cmds = constructLaunchCommands(launchArguments, false, null);
        RunInTerminalRequestArguments requestArgs = null;
        if (launchArguments.console == CONSOLE.integratedTerminal) {
            requestArgs = RunInTerminalRequestArguments.createIntegratedTerminal(cmds, launchArguments.cwd,
                    launchArguments.env, TERMINAL_TITLE);
        } else {
            requestArgs = RunInTerminalRequestArguments.createExternalTerminal(cmds, launchArguments.cwd,
                    launchArguments.env, TERMINAL_TITLE);
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
                    // Without knowing the pid, debugger has lost control of the process.
                    // So simply send `terminated` event to end the session.
                    context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
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

    private CompletableFuture<Response> handleDisconnectCommand(Arguments arguments, Response response,
            IDebugAdapterContext context) {
        DisconnectArguments disconnectArguments = (DisconnectArguments) arguments;
        if (debuggeeProcess != null && disconnectArguments.terminateDebuggee && !context.isAttached()) {
            debuggeeProcess.destroy();
        }
        return CompletableFuture.completedFuture(response);

    }
}
