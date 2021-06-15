/*******************************************************************************
 * Copyright (c) 2017-2021 Microsoft Corporation and others.
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
import java.util.Map;

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
    private static final int ACCEPT_TIMEOUT = 10 * 1000;

    public AdvancedLaunchingConnector(VirtualMachineManagerImpl virtualMachineManager) {
        super(virtualMachineManager);
    }

    @Override
    public Map<String, Argument> defaultArguments() {
        Map<String, Argument> defaultArgs = super.defaultArguments();

        Argument cwdArg = new JDIStringArgumentImpl(DebugUtility.CWD, "Current working directory", DebugUtility.CWD, false);
        cwdArg.setValue(null);
        defaultArgs.put(DebugUtility.CWD, cwdArg);

        Argument envArg = new JDIStringArgumentImpl(DebugUtility.ENV, "Environment variables", DebugUtility.ENV, false);
        envArg.setValue(null);
        defaultArgs.put(DebugUtility.ENV, envArg);

        return defaultArgs;
    }

    @Override
    public String name() {
        return "com.microsoft.java.debug.AdvancedLaunchingConnector";
    }

    @Override
    public VirtualMachine launch(Map<String, ? extends Argument> connectionArgs)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        String cwd = connectionArgs.get(DebugUtility.CWD).value();
        File workingDir = null;
        if (cwd != null && Files.isDirectory(Paths.get(cwd))) {
            workingDir = new File(cwd);
        }

        String[] envVars = null;
        try {
            envVars = DebugUtility.decodeArrayArgument(connectionArgs.get(DebugUtility.ENV).value());
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
        final String javaHome = launchingOptions.get(DebugUtility.HOME).value();
        final String javaExec = launchingOptions.get(DebugUtility.EXEC).value();
        final String slash = System.getProperty("file.separator");
        boolean suspend = Boolean.valueOf(launchingOptions.get(DebugUtility.SUSPEND).value());
        final String javaOptions = launchingOptions.get(DebugUtility.OPTIONS).value();
        final String main = launchingOptions.get(DebugUtility.MAIN).value();

        StringBuilder execString = new StringBuilder();
        execString.append("\"" + javaHome + slash + "bin" + slash + javaExec + "\"");
        execString.append(" -Xdebug -Xnoagent -Djava.compiler=NONE");
        execString.append(" -Xrunjdwp:transport=dt_socket,address=" + address + ",server=n,suspend=" + (suspend ? "y" : "n"));
        if (javaOptions != null) {
            execString.append(" " + javaOptions);
        }
        execString.append(" " + main);

        return DebugUtility.parseArguments(execString.toString()).toArray(new String[0]);
    }

    abstract class JDIArgumentImpl implements Argument {
        private static final long serialVersionUID = 8850533280769854833L;
        private String name;
        private String description;
        private String label;
        private boolean mustSpecify;

        protected JDIArgumentImpl(String name, String description, String label, boolean mustSpecify) {
            this.name = name;
            this.description = description;
            this.label = label;
            this.mustSpecify = mustSpecify;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public boolean mustSpecify() {
            return mustSpecify;
        }

        @Override
        public abstract String value();

        @Override
        public abstract void setValue(String value);

        @Override
        public abstract boolean isValid(String value);

        @Override
        public abstract String toString();
    }

    class JDIStringArgumentImpl extends JDIArgumentImpl implements StringArgument {
        private static final long serialVersionUID = 6009335074727417445L;
        private String value;

        protected JDIStringArgumentImpl(String name, String description, String label, boolean mustSpecify) {
            super(name, description, label, mustSpecify);
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean isValid(String value) {
            return true;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
