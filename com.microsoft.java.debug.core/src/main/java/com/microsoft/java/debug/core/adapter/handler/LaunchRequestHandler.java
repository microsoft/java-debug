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
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.LaunchException;
import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.LaunchMode;
import com.microsoft.java.debug.core.adapter.ProcessConsole;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Events.OutputEvent;
import com.microsoft.java.debug.core.protocol.Events.OutputEvent.Category;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.CONSOLE;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.LaunchArguments;
import com.microsoft.java.debug.core.protocol.Requests.ShortenApproach;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.VMDisconnectEvent;

public class LaunchRequestHandler implements IDebugRequestHandler {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    protected ILaunchDelegate activeLaunchHandler;
    private CompletableFuture<Boolean> waitForDebuggeeConsole = new CompletableFuture<>();

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.LAUNCH);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        if (!context.isInitialized()) {
            final String errorMessage = "'launch' request is rejected since the debug session has not been initialized yet.";
            logger.log(Level.SEVERE, errorMessage);
            return CompletableFuture.completedFuture(
                AdapterUtils.setErrorResponse(response, ErrorCode.LAUNCH_FAILURE, errorMessage));
        }
        LaunchArguments launchArguments = (LaunchArguments) arguments;
        Map<String, Object> traceInfo = new HashMap<>();
        traceInfo.put("asyncJDWP", context.asyncJDWP());
        traceInfo.put("noDebug", launchArguments.noDebug);
        traceInfo.put("console", launchArguments.console);
        UsageDataSession.recordInfo("launch debug info", traceInfo);

        activeLaunchHandler = launchArguments.noDebug ? new LaunchWithoutDebuggingDelegate((daContext) -> handleTerminatedEvent(daContext))
                : new LaunchWithDebuggingDelegate();
        return handleLaunchCommand(arguments, response, context);
    }

    protected CompletableFuture<Response> handleLaunchCommand(Arguments arguments, Response response, IDebugAdapterContext context) {
        LaunchArguments launchArguments = (LaunchArguments) arguments;
        // validation
        if (StringUtils.isBlank(launchArguments.mainClass)
                || ArrayUtils.isEmpty(launchArguments.modulePaths) && ArrayUtils.isEmpty(launchArguments.classPaths)) {
            throw AdapterUtils.createCompletionException(
                "Failed to launch debuggee VM. Missing mainClass or modulePaths/classPaths options in launch configuration.",
                ErrorCode.ARGUMENT_MISSING);
        }
        if (StringUtils.isNotBlank(launchArguments.encoding)) {
            if (!Charset.isSupported(launchArguments.encoding)) {
                throw AdapterUtils.createCompletionException(
                    "Failed to launch debuggee VM. 'encoding' options in the launch configuration is not recognized.",
                    ErrorCode.INVALID_ENCODING);
            }
            context.setDebuggeeEncoding(Charset.forName(launchArguments.encoding));
            if (StringUtils.isBlank(launchArguments.vmArgs)) {
                launchArguments.vmArgs = String.format("-Dfile.encoding=%s", context.getDebuggeeEncoding().name());
            } else {
                // if vmArgs already has the file.encoding settings, duplicate options for jvm will not cause an error, the right most value wins
                launchArguments.vmArgs = String.format("%s -Dfile.encoding=%s", launchArguments.vmArgs, context.getDebuggeeEncoding().name());
            }
        }

        context.setLaunchMode(launchArguments.noDebug ? LaunchMode.NO_DEBUG : LaunchMode.DEBUG);

        activeLaunchHandler.preLaunch(launchArguments, context);

        // Use the specified cli style to launch the program.
        if (launchArguments.shortenCommandLine == ShortenApproach.JARMANIFEST) {
            if (ArrayUtils.isNotEmpty(launchArguments.classPaths)) {
                try {
                    Path tempfile = LaunchUtils.generateClasspathJar(launchArguments.classPaths);
                    launchArguments.vmArgs += " -cp \"" + tempfile.toAbsolutePath().toString() + "\"";
                    launchArguments.classPaths = new String[0];
                    context.setClasspathJar(tempfile);
                } catch (IllegalArgumentException | MalformedURLException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to launch the program with jarmanifest style: %s", ex.toString(), ex));
                    throw AdapterUtils.createCompletionException("Failed to launch the program with jarmanifest style: " + ex.toString(),
                            ErrorCode.LAUNCH_FAILURE, ex);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, String.format("Failed to create a temp classpath.jar: %s", e.toString()), e);
                }
            }
        } else if (launchArguments.shortenCommandLine == ShortenApproach.ARGFILE) {
            try {
                /**
                 * See the JDK spec https://docs.oracle.com/en/java/javase/18/docs/specs/man/java.html#java-command-line-argument-files.
                 * The argument file must contain only ASCII characters or characters in system default encoding that's ASCII friendly.
                 */
                Charset systemCharset = LaunchUtils.getSystemCharset();
                CharsetEncoder encoder = systemCharset.newEncoder();
                String vmArgsForShorten = null;
                String[] classPathsForShorten = null;
                String[] modulePathsForShorten = null;
                if (StringUtils.isNotBlank(launchArguments.vmArgs)) {
                    if (!encoder.canEncode(launchArguments.vmArgs)) {
                        logger.warning(String.format("Cannot generate the 'vmArgs' argument into the argfile because it contains characters "
                            + "that cannot be encoded in the system charset '%s'.", systemCharset.displayName()));
                    } else {
                        vmArgsForShorten = launchArguments.vmArgs;
                    }
                }

                if (ArrayUtils.isNotEmpty(launchArguments.classPaths)) {
                    if (!encoder.canEncode(String.join(File.pathSeparator, launchArguments.classPaths))) {
                        logger.warning(String.format("Cannot generate the '-cp' argument into the argfile because it contains characters "
                            + "that cannot be encoded in the system charset '%s'.", systemCharset.displayName()));
                    } else {
                        classPathsForShorten = launchArguments.classPaths;
                    }
                }

                if (ArrayUtils.isNotEmpty(launchArguments.modulePaths)) {
                    if (!encoder.canEncode(String.join(File.pathSeparator, launchArguments.modulePaths))) {
                        logger.warning(String.format("Cannot generate the '--module-path' argument into the argfile because it contains characters "
                            + "that cannot be encoded in the system charset '%s'.", systemCharset.displayName()));
                    } else {
                        modulePathsForShorten = launchArguments.modulePaths;
                    }
                }

                if (vmArgsForShorten != null || classPathsForShorten != null || modulePathsForShorten != null) {
                    Path tempfile = LaunchUtils.generateArgfile(vmArgsForShorten, classPathsForShorten, modulePathsForShorten, systemCharset);
                    launchArguments.vmArgs = (vmArgsForShorten == null ? launchArguments.vmArgs : "")
                                                + " \"@" + tempfile.toAbsolutePath().toString() + "\"";
                    launchArguments.classPaths = (classPathsForShorten == null ? launchArguments.classPaths : new String[0]);
                    launchArguments.modulePaths = (modulePathsForShorten == null ? launchArguments.modulePaths : new String[0]);
                    context.setArgsfile(tempfile);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, String.format("Failed to create a temp argfile: %s", e.toString()), e);
            }
        }

        return launch(launchArguments, response, context).thenCompose(res -> {
            long processId = context.getProcessId();
            long shellProcessId = context.getShellProcessId();
            if (context.getDebuggeeProcess() != null) {
                processId = context.getDebuggeeProcess().pid();
            }

            // If processId or shellProcessId exist, send a notification to client.
            if (processId > 0 || shellProcessId > 0) {
                context.getProtocolServer().sendEvent(new Events.ProcessIdNotification(processId, shellProcessId));
            }

            LaunchUtils.releaseTempLaunchFile(context.getClasspathJar());
            LaunchUtils.releaseTempLaunchFile(context.getArgsfile());
            if (res.success) {
                activeLaunchHandler.postLaunch(launchArguments, context);
            }

            IDebugSession debugSession = context.getDebugSession();
            if (debugSession != null) {
                debugSession.getEventHub().events()
                    .filter((debugEvent) -> debugEvent.event instanceof VMDisconnectEvent)
                    .subscribe((debugEvent) -> {
                        context.setVmTerminated();
                        // Terminate eventHub thread.
                        try {
                            debugSession.getEventHub().close();
                        } catch (Exception e) {
                            // do nothing.
                        }

                        handleTerminatedEvent(context);
                    });
            }
            return CompletableFuture.completedFuture(res);
        });
    }

    protected void handleTerminatedEvent(IDebugAdapterContext context) {
        CompletableFuture.runAsync(() -> {
            try {
                waitForDebuggeeConsole.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                // do nothing.
            }

            context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
        });
    }

    /**
     * Construct the Java command lines based on the given launch arguments.
     * @param launchArguments - The launch arguments
     * @param serverMode - whether to enable the debug port with server mode
     * @param address - the debug port
     * @return the command arrays
     */
    public static String[] constructLaunchCommands(LaunchArguments launchArguments, boolean serverMode, String address) {
        List<String> launchCmds = new ArrayList<>();
        if (launchArguments.launcherScript != null) {
            launchCmds.add(launchArguments.launcherScript);
        }

        if (StringUtils.isNotBlank(launchArguments.javaExec)) {
            launchCmds.add(launchArguments.javaExec);
        } else {
            final String javaHome = StringUtils.isNotEmpty(DebugSettings.getCurrent().javaHome) ? DebugSettings.getCurrent().javaHome
                    : System.getProperty("java.home");
            launchCmds.add(Paths.get(javaHome, "bin", "java").toString());
        }
        if (StringUtils.isNotEmpty(address)) {
            launchCmds.add(String.format("-agentlib:jdwp=transport=dt_socket,server=%s,suspend=y,address=%s", serverMode ? "y" : "n", address));
        }
        if (StringUtils.isNotBlank(launchArguments.vmArgs)) {
            launchCmds.addAll(DebugUtility.parseArguments(launchArguments.vmArgs));
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
        if (mainClasses.length == 2) {
            launchCmds.add("-m");
        }
        launchCmds.add(launchArguments.mainClass);
        if (StringUtils.isNotBlank(launchArguments.args)) {
            launchCmds.addAll(DebugUtility.parseArguments(launchArguments.args));
        }
        return launchCmds.toArray(new String[0]);
    }

    protected CompletableFuture<Response> launch(LaunchArguments launchArguments, Response response, IDebugAdapterContext context) {
        logger.info("Trying to launch Java Program with options:\n" + String.format("main-class: %s\n", launchArguments.mainClass)
                + String.format("args: %s\n", launchArguments.args)
                + String.format("module-path: %s\n", StringUtils.join(launchArguments.modulePaths, File.pathSeparator))
                + String.format("class-path: %s\n", StringUtils.join(launchArguments.classPaths, File.pathSeparator))
                + String.format("vmArgs: %s", launchArguments.vmArgs));

        if (context.supportsRunInTerminalRequest()
                && (launchArguments.console == CONSOLE.integratedTerminal || launchArguments.console == CONSOLE.externalTerminal)) {
            waitForDebuggeeConsole.complete(true);
            return activeLaunchHandler.launchInTerminal(launchArguments, response, context);
        }

        CompletableFuture<Response> resultFuture = new CompletableFuture<>();
        try {
            Process debuggeeProcess = activeLaunchHandler.launch(launchArguments, context);
            context.setDebuggeeProcess(debuggeeProcess);
            ProcessConsole debuggeeConsole = new ProcessConsole(debuggeeProcess, "Debuggee", context.getDebuggeeEncoding());
            debuggeeConsole.lineMessages()
                .map((message) -> convertToOutputEvent(message.output, message.category, context))
                .doFinally(() -> waitForDebuggeeConsole.complete(true))
                .subscribe((event) -> context.getProtocolServer().sendEvent(event));
            debuggeeConsole.start();
            resultFuture.complete(response);
        } catch (LaunchException e) {
            if (StringUtils.isNotBlank(e.getStdout())) {
                OutputEvent event = convertToOutputEvent(e.getStdout(), Category.stdout, context);
                context.getProtocolServer().sendEvent(event);
            }
            if (StringUtils.isNotBlank(e.getStderr())) {
                OutputEvent event = convertToOutputEvent(e.getStderr(), Category.stderr, context);
                context.getProtocolServer().sendEvent(event);
            }

            resultFuture.completeExceptionally(
                    new DebugException(
                            String.format("Failed to launch debuggee VM. Reason: %s", e.getMessage()),
                            ErrorCode.LAUNCH_FAILURE.getId()
                    )
            );
        } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
            resultFuture.completeExceptionally(
                    new DebugException(
                            String.format("Failed to launch debuggee VM. Reason: %s", e.toString()),
                            ErrorCode.LAUNCH_FAILURE.getId()
                    )
            );
        }

        return resultFuture;
    }

    private static final Pattern STACKTRACE_PATTERN = Pattern.compile("\\s+at\\s+([\\w$\\.]+\\/)?(([\\w$]+\\.)+[<\\w$>]+)\\(([\\w-$]+\\.java:\\d+)\\)");

    private static OutputEvent convertToOutputEvent(String message, Category category, IDebugAdapterContext context) {
        Matcher matcher = STACKTRACE_PATTERN.matcher(message);
        if (matcher.find()) {
            String methodField = matcher.group(2);
            String locationField = matcher.group(matcher.groupCount());
            String fullyQualifiedName = methodField.substring(0, methodField.lastIndexOf("."));
            String packageName = fullyQualifiedName.lastIndexOf(".") > -1 ? fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf(".")) : "";
            String[] locations = locationField.split(":");
            String sourceName = locations[0];
            int lineNumber = Integer.parseInt(locations[1]);
            String sourcePath = StringUtils.isBlank(packageName) ? sourceName
                    : packageName.replace('.', File.separatorChar) + File.separatorChar + sourceName;
            Types.Source source = null;
            try {
                source = StackTraceRequestHandler.convertDebuggerSourceToClient(fullyQualifiedName, sourceName, sourcePath, context);
            } catch (URISyntaxException e) {
                // do nothing.
            }

            return new OutputEvent(category, message, source, lineNumber);
        }

        return new OutputEvent(category, message);
    }

    protected static String[] constructEnvironmentVariables(LaunchArguments launchArguments) {
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
        return envVars;
    }

    public static String parseMainClassWithoutModuleName(String mainClass) {
        int index = mainClass.indexOf('/');
        return mainClass.substring(index + 1);
    }
}
