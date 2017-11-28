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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonObject;
import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugSession;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.adapter.ProcessConsole;
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

public class LaunchRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final int RUNINTERMINAL_TIMEOUT = 10 * 1000;
    private static final int ACCEPT_TIMEOUT = 10 * 1000;
    private static final String TERMINAL_TITLE = "Java Debug Console";

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.LAUNCH);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        LaunchArguments launchArguments = (LaunchArguments) arguments;
        if (StringUtils.isBlank(launchArguments.mainClass)
                || (ArrayUtils.isEmpty(launchArguments.modulePaths) && ArrayUtils.isEmpty(launchArguments.classPaths))) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                       String.format("Failed to launch debuggee VM. Missing mainClass or modulePaths/classPaths options in launch configuration"));
        }

        context.setAttached(false);
        context.setVmStopOnEntry(launchArguments.stopOnEntry);
        context.setMainClass(parseMainClassWithoutModuleName(launchArguments.mainClass));

        if (StringUtils.isBlank(launchArguments.encoding)) {
            context.setDebuggeeEncoding(StandardCharsets.UTF_8);
        } else {
            if (!Charset.isSupported(launchArguments.encoding)) {
                return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.INVALID_ENCODING,
                            String.format("Failed to launch debuggee VM. 'encoding' options in the launch configuration is not recognized."));
            }

            context.setDebuggeeEncoding(Charset.forName(launchArguments.encoding));
        }

        if (StringUtils.isBlank(launchArguments.vmArgs)) {
            launchArguments.vmArgs = String.format("-Dfile.encoding=%s", context.getDebuggeeEncoding().name());
        } else {
            // if vmArgs already has the file.encoding settings, duplicate options for jvm will not cause an error, the right most value wins
            launchArguments.vmArgs = String.format("%s -Dfile.encoding=%s", launchArguments.vmArgs, context.getDebuggeeEncoding().name());
        }

        // Append environment to native environment.
        String[] envVars = null;
        if (launchArguments.env != null && !launchArguments.env.isEmpty()) {
            Map<String, String> environment = new HashMap<>(System.getenv());
            List<String> duplicated = new ArrayList<>();
            for (Entry<String, String> entry : launchArguments.env.entrySet()) {
                if (environment.containsKey(entry.getKey())) {
                    duplicated.add(entry.getKey());
                }
                environment.put(entry.getKey(), entry.getValue());
            }
            // For duplicated variables, show a warning message.
            if (!duplicated.isEmpty()) {
                logger.warning(String.format("There are duplicated environment variables. The values specified in launch.json will be used. "
                        + "Here are the duplicated entries: %s.", String.join(",", duplicated)));
            }

            envVars = new String[environment.size()];
            int i = 0;
            for (Entry<String, String> entry : environment.entrySet()) {
                envVars[i++] = entry.getKey() + "=" + entry.getValue();
            }
        }

        logger.info("Trying to launch Java Program with options:\n" + String.format("main-class: %s\n", launchArguments.mainClass)
                + String.format("args: %s\n", launchArguments.args)
                + String.format("module-path: %s\n", StringUtils.join(launchArguments.modulePaths, File.pathSeparator))
                + String.format("class-path: %s\n", StringUtils.join(launchArguments.classPaths, File.pathSeparator))
                + String.format("vmArgs: %s", launchArguments.vmArgs));

        try {
            IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);

            if (context.supportsRunInTerminalRequest()
                    && (launchArguments.console == CONSOLE.integratedTerminal || launchArguments.console == CONSOLE.externalTerminal)) {
                CompletableFuture<Response> resultFuture = new CompletableFuture<>();

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
                context.sendRequest(request, RUNINTERMINAL_TIMEOUT, (runResponse) -> {
                    if (runResponse.success) {
                        try {
                            VirtualMachine vm = listenConnector.accept(args);
                            context.setDebugSession(new DebugSession(vm));
                            logger.info("Launching debuggee in terminal console succeeded.");
                            resultFuture.complete(response);
                        } catch (IOException | IllegalConnectorArgumentsException e) {
                            logger.log(Level.SEVERE, String.format("Failed to launch debuggee in terminal. Reason: %s", e.toString()));
                            resultFuture.complete(AdapterUtils.setErrorResponse(response, ErrorCode.LAUNCH_IN_TERMINAL_FAILURE,
                                    String.format("Failed to launch debuggee in terminal. Reason: %s", e.toString())));
                        }
                    } else {
                        logger.log(Level.SEVERE, String.format("Failed to launch debuggee in terminal. Reason: %s", runResponse.message));
                        resultFuture.complete(AdapterUtils.setErrorResponse(response, ErrorCode.LAUNCH_IN_TERMINAL_FAILURE,
                                String.format("Failed to launch debuggee in terminal. Reason: %s", runResponse.message)));
                    }
                });

                return resultFuture;
            } else {
                IDebugSession debugSession = DebugUtility.launch(
                        vmProvider.getVirtualMachineManager(),
                        launchArguments.mainClass,
                        launchArguments.args,
                        launchArguments.vmArgs,
                        Arrays.asList(launchArguments.modulePaths),
                        Arrays.asList(launchArguments.classPaths),
                        launchArguments.cwd,
                        envVars);
                context.setDebugSession(debugSession);

                logger.info("Launching debuggee VM succeeded.");

                ProcessConsole debuggeeConsole = new ProcessConsole(debugSession.process(), "Debuggee", context.getDebuggeeEncoding());
                debuggeeConsole.onStdout((output) -> {
                    // When DA receives a new OutputEvent, it just shows that on Debug Console and doesn't affect the DA's dispatching workflow.
                    // That means the debugger can send OutputEvent to DA at any time.
                    context.sendEvent(Events.OutputEvent.createStdoutOutput(output));
                });

                debuggeeConsole.onStderr((err) -> {
                    context.sendEvent(Events.OutputEvent.createStderrOutput(err));
                });
                debuggeeConsole.start();

                return CompletableFuture.completedFuture(response);
            }
        } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
            logger.log(Level.SEVERE, String.format("Failed to launch debuggee VM. Reason: %s", e.toString()));
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.LAUNCH_FAILURE,
                        String.format("Failed to launch debuggee VM. Reason: %s", e.toString()));
        }
    }

    private static String parseMainClassWithoutModuleName(String mainClass) {
        int index = mainClass.indexOf('/');
        return mainClass.substring(index + 1);
    }

    private String[] constructLaunchCommands(LaunchArguments launchArguments, boolean serverMode, String address) {
        String slash = System.getProperty("file.separator");

        List<String> launchCmds = new ArrayList<>();
        launchCmds.add(System.getProperty("java.home") + slash + "bin" + slash + "java");
        launchCmds.add(String.format("-agentlib:jdwp=transport=dt_socket,server=%s,suspend=y,address=%s", (serverMode ? "y" : "n"), address));
        if (StringUtils.isNotBlank(launchArguments.vmArgs)) {
            launchCmds.addAll(parseArguments(launchArguments.vmArgs));
        }
        if (ArrayUtils.isNotEmpty(launchArguments.modulePaths)) {
            launchCmds.add("--module-path");
            launchCmds.add(String.join(File.pathSeparator, launchArguments.modulePaths));
        }
        if (ArrayUtils.isNotEmpty(launchArguments.classPaths)) {
            launchCmds.add("-cp");
            launchCmds.add(String.join(File.pathSeparator, launchArguments.classPaths));
        }
        // For java 9 project, should specify "-m $MainClass".
        String[] mainClasses = launchArguments.mainClass.split("/");
        if (ArrayUtils.isNotEmpty(launchArguments.modulePaths) || mainClasses.length == 2) {
            launchCmds.add("-m");
        }
        launchCmds.add(launchArguments.mainClass);
        if (StringUtils.isNotBlank(launchArguments.args)) {
            launchCmds.addAll(parseArguments(launchArguments.args));
        }
        return launchCmds.toArray(new String[0]);
    }

    /**
     * Parses the given command line into separate arguments that can be passed
     * to <code>Runtime.getRuntime().exec(cmdArray)</code>.
     *
     * @param args command line as a single string.
     * @return the arguments array.
     */
    private static List<String> parseArguments(String cmdStr) {
        List<String> list = new ArrayList<String>();
        // The legal arguments are
        // 1. token starting with something other than quote " and followed by zero or more non-space characters
        // 2. a quote " followed by whatever, until another quote "
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(cmdStr);
        while (m.find()) {
            String arg = m.group(1).replaceAll("^\"|\"$", ""); // Remove surrounding quotes.
            list.add(arg);
        }
        return list;
    }
}
