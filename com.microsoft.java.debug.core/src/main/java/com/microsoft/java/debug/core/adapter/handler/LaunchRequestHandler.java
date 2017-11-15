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
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.adapter.ProcessConsole;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Messages;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
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

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.LAUNCH);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        LaunchArguments launchArguments = (LaunchArguments) arguments;
        if (StringUtils.isBlank(launchArguments.mainClass)
                || (ArrayUtils.isEmpty(launchArguments.modulePaths) && ArrayUtils.isEmpty(launchArguments.classPaths))) {
            AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                    String.format("Failed to launch debuggee VM. Missing mainClass or modulePaths/classPaths options in launch configuration"));
            return;
        }

        context.setAttached(false);
        context.setSourcePaths(launchArguments.sourcePaths);

        if (StringUtils.isBlank(launchArguments.encoding)) {
            context.setDebuggeeEncoding(StandardCharsets.UTF_8);
        } else {
            if (!Charset.isSupported(launchArguments.encoding)) {
                AdapterUtils.setErrorResponse(response, ErrorCode.INVALID_ENCODING,
                        String.format("Failed to launch debuggee VM. 'encoding' options in the launch configuration is not recognized."));
                return;
            }

            context.setDebuggeeEncoding(Charset.forName(launchArguments.encoding));
        }


        if (StringUtils.isBlank(launchArguments.vmArgs)) {
            launchArguments.vmArgs = String.format("-Dfile.encoding=%s", context.getDebuggeeEncoding().name());
        } else {
            // if vmArgs already has the file.encoding settings, duplicate options for jvm will not cause an error, the right most value wins
            launchArguments.vmArgs = String.format("%s -Dfile.encoding=%s", launchArguments.vmArgs, context.getDebuggeeEncoding().name());
        }


        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);


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

        try {
            StringBuilder launchLogs = new StringBuilder();
            launchLogs.append("Trying to launch Java Program with options:\n");
            launchLogs.append(String.format("main-class: %s\n", launchArguments.mainClass));
            launchLogs.append(String.format("args: %s\n", launchArguments.args));
            launchLogs.append(String.format("module-path: %s\n", StringUtils.join(launchArguments.modulePaths, File.pathSeparator)));
            launchLogs.append(String.format("class-path: %s\n", StringUtils.join(launchArguments.classPaths, File.pathSeparator)));
            launchLogs.append(String.format("vmArgs: %s", launchArguments.vmArgs));
            logger.info(launchLogs.toString());

            if (context.isSupportsRunInTerminalRequest()
                    && (launchArguments.console.equals("integratedTerminal") || launchArguments.console.equals("externalTerminal"))) {
                final int ACCEPT_TIMEOUT = 10 * 1000;
                List<ListeningConnector> connectors = vmProvider.getVirtualMachineManager().listeningConnectors();
                final ListeningConnector listenConnector = connectors.get(0);
                Map<String, Connector.Argument> args = listenConnector.defaultArguments();
                ((Connector.IntegerArgument) args.get("timeout")).setValue(ACCEPT_TIMEOUT);
                String address = listenConnector.startListening(args);

                String[] cmds = constructLaunchCommands(launchArguments, address);
                RunInTerminalRequestArguments requestArgs = null;
                if (launchArguments.console.equals("integratedTerminal")) {
                    requestArgs = RunInTerminalRequestArguments.createIntegratedTerminal(
                            cmds,
                            launchArguments.cwd,
                            launchArguments.env,
                            Constants.JAVA_DEBUG_CONSOLE);
                } else {
                    requestArgs = RunInTerminalRequestArguments.createExternalTerminal(
                            cmds,
                            launchArguments.cwd,
                            launchArguments.env,
                            Constants.JAVA_DEBUG_CONSOLE);
                }
                Messages.Request request = new Messages.Request(
                        Requests.Command.RUNINTERMINAL.getName(),
                        (JsonObject) JsonUtils.toJsonTree(requestArgs, RunInTerminalRequestArguments.class));

                // The DA will delegate the execution to the shell, but it doesn't promise to return the process id and exit code to debugger.
                // This is because the DA cannot really know whether the launching of the target was successful and whether the target is ready
                // for debugging. For this reason the debugger cannot depend on the runInTerminal response to determine if the debuggee is ready or not.
                // The debugger will create a listening connector in server mode, and let the debuggee that was started on the shell to
                // connect to the connector server. And it times out after 10 seconds.
                context.sendRequest(request, (runResponse) -> {
                    logger.info("runInTerminalRequest response.");
                });

                try {
                    VirtualMachine virtualMachine = listenConnector.accept(args);
                    DebugSession debugSession = new DebugSession(virtualMachine);
                    context.setDebugSession(debugSession);

                    logger.info("Launching debuggee VM in external console succeeded.");
                } catch (IOException | IllegalConnectorArgumentsException e) {
                    AdapterUtils.setErrorResponse(response, ErrorCode.LAUNCH_FAILURE,
                            String.format("Failed to launch debuggee VM. Reason: %s", e.toString()));
                }
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
            }
        } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
            AdapterUtils.setErrorResponse(response, ErrorCode.LAUNCH_FAILURE,
                    String.format("Failed to launch debuggee VM. Reason: %s", e.toString()));
        }

        context.setVmStopOnEntry(launchArguments.stopOnEntry);
        context.setMainClass(parseMainClassWithoutModuleName(launchArguments.mainClass));

        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        Map<String, Object> options = new HashMap<>();
        options.put(Constants.DEBUGGEE_ENCODING, context.getDebuggeeEncoding());
        if (launchArguments.projectName != null) {
            options.put(Constants.PROJECTNAME, launchArguments.projectName);
        }
        sourceProvider.initialize(context.getDebugSession(), options);
    }

    private static String parseMainClassWithoutModuleName(String mainClass) {
        int index = mainClass.indexOf('/');
        return mainClass.substring(index + 1);
    }

    private String[] constructLaunchCommands(LaunchArguments launchArguments, String address) {
        String slash = System.getProperty("file.separator");

        List<String> launchCmds = new ArrayList<>();
        launchCmds.add(System.getProperty("java.home") + slash + "bin" + slash + "java");
        launchCmds.add("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + address);
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
