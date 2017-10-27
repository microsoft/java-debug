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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdi.internal.VirtualMachineImpl;
import org.eclipse.jdi.internal.VirtualMachineManagerImpl;
import org.eclipse.jdi.internal.connect.SocketLaunchingConnectorImpl;
import org.eclipse.jdi.internal.connect.SocketListeningConnectorImpl;

import com.microsoft.java.debug.core.DebugUtility;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;

/**
 * An advanced launching connector that supports cwd and enviroment variables.
 *
 */
public class AdvancedLaunchingConnector extends SocketLaunchingConnectorImpl implements LaunchingConnector {
    public static final String HOME = "home";
    public static final String OPTIONS = "options";
    public static final String MAIN = "main";
    public static final String SUSPEND = "suspend";
    public static final String QUOTE = "quote";
    public static final String EXEC = "vmexec";
    private static final String CWD = "cwd";
    private static final String ENV = "env";
    private static final int ACCEPT_TIMEOUT = 10 * 1000;

    public AdvancedLaunchingConnector(VirtualMachineManagerImpl virtualMachineManager) {
        super(virtualMachineManager);
    }

    @Override
    public Map<String, Argument> defaultArguments() {
        Map<String, Argument> defaultArgs = super.defaultArguments();

        Argument cwdArg = new AdvancedStringArgumentImpl(CWD, "Current working directory", CWD, false);
        cwdArg.setValue(null);
        defaultArgs.put(CWD, cwdArg);

        Argument envArg = new AdvancedStringArgumentImpl(ENV, "Environment variables", ENV, false);
        envArg.setValue(null);
        defaultArgs.put(ENV, envArg);

        return defaultArgs;
    }

    @Override
    public String name() {
        return "com.microsoft.java.debug.AdvancedLaunchingConnector";
    }

    @Override
    public VirtualMachine launch(Map<String, ? extends Argument> connectionArgs)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        String cwd = connectionArgs.get(CWD).value();
        File workingDir = null;
        if (cwd != null && Files.isDirectory(Paths.get(cwd))) {
            workingDir = new File(cwd);
        }

        String[] envVars = null;
        try {
            envVars = DebugUtility.decodeArrayArgument(connectionArgs.get(ENV).value());
        } catch (IllegalArgumentException e) {
            // do nothing.
        }

        SocketListeningConnectorImpl listenConnector = new SocketListeningConnectorImpl(
                virtualMachineManager());
        Map<String, Connector.Argument> args = listenConnector.defaultArguments();
        ((Connector.IntegerArgument) args.get("timeout")).setValue(ACCEPT_TIMEOUT); //$NON-NLS-1$
        String address = listenConnector.startListening(args);

        String[] cmds = constructLaunchCommand(connectionArgs, address);
        Process process = Runtime.getRuntime().exec(cmds, envVars, workingDir);

        VirtualMachineImpl vm;
        try {
            vm = (VirtualMachineImpl) listenConnector.accept(args);
        } catch (IOException | IllegalConnectorArgumentsException e) {
            process.destroy();
            throw new VMStartException(String.format("VM did not connect within given time: %d ms", ACCEPT_TIMEOUT), process);
        }

        vm.setLaunchedProcess(process);
        return vm;
    }

    private static String[] constructLaunchCommand(Map<String, ? extends Argument> launchingOptions, String address) {
        String javaHome = launchingOptions.get(HOME).value();
        String javaOptions = launchingOptions.get(OPTIONS).value();
        String main = launchingOptions.get(MAIN).value();
        boolean suspend = Boolean.valueOf(launchingOptions.get(SUSPEND).value());
        String javaExec = launchingOptions.get(EXEC).value();
        String slash = System.getProperty("file.separator");

        List<String> cmds = new ArrayList<>();
        cmds.add(javaHome + slash + "bin" + slash + javaExec);
        cmds.addAll(parseArguments("-Xdebug -Xnoagent -Djava.compiler=NONE"));
        cmds.add("-Xrunjdwp:transport=dt_socket,address=" + address + ",server=n,suspend=" + (suspend ? "y" : "n"));
        cmds.addAll(parseArguments(javaOptions));
        cmds.addAll(parseArguments(main));

        return cmds.toArray(new String[0]);
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

    class AdvancedStringArgumentImpl extends StringArgumentImpl implements StringArgument {
        private static final long serialVersionUID = 1L;

        protected AdvancedStringArgumentImpl(String name, String description, String label, boolean mustSpecify) {
            super(name, description, label, mustSpecify);
        }
    }
}
