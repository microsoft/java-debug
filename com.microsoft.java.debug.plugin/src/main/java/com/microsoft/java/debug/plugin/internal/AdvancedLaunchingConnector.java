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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdi.internal.VirtualMachineImpl;
import org.eclipse.jdi.internal.VirtualMachineManagerImpl;
import org.eclipse.jdi.internal.connect.SocketLaunchingConnectorImpl;
import org.eclipse.jdi.internal.connect.SocketListeningConnectorImpl;

import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.LaunchException;
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

        /* Launch the Java process */
        final Process process = Runtime.getRuntime().exec(cmds, envVars, workingDir);

        /* A Future that will be completed if we successfully connect to the launched process, or
           will fail with an Exception if we do not.
         */
        final CompletableFuture<VirtualMachineImpl> result = new CompletableFuture<>();

        /* Listen for the debug connection from the Java process */
        ForkJoinPool.commonPool().execute(() -> {
            try {
                VirtualMachineImpl vm = (VirtualMachineImpl) listenConnector.accept(args);
                vm.setLaunchedProcess(process);
                result.complete(vm);
            } catch (IllegalConnectorArgumentsException e) {
                result.completeExceptionally(e);
            } catch (IOException e) {
                if (result.isDone()) {
                    /* The result Future has already been completed by the Process onExit hook */
                    return;
                }

                final String stdout = streamToString(process.getInputStream());
                final String stderr = streamToString(process.getErrorStream());

                process.destroy();

                result.completeExceptionally(new LaunchException(
                    String.format("VM did not connect within given time: %d ms", ACCEPT_TIMEOUT),
                    process,
                    false,
                    -1,
                    stdout,
                    stderr
                ));
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });

        /* Wait for the Java process to exit; if it exits before the debug connection is made, report it as an error. */
        process.onExit().thenAcceptAsync(theProcess -> {
            if (result.isDone()) {
                /* The result Future has already been completed by successfully connecting to the debug connection */
                return;
            }

            final int exitStatus = theProcess.exitValue();
            final String stdout = streamToString(process.getInputStream());
            final String stderr = streamToString(process.getErrorStream());

            result.completeExceptionally(new LaunchException(
                String.format("VM exited with status %d", exitStatus),
                theProcess,
                true,
                exitStatus,
                stdout,
                stderr
            ));

            /* Stop the debug connection attempt */
            try {
                listenConnector.stopListening(args);
            } catch (IOException e) {
                /* Ignore */
            }
        });

        try {
            return result.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else if (e.getCause() instanceof IllegalConnectorArgumentsException) {
                throw (IllegalConnectorArgumentsException) e.getCause();
            } else if (e.getCause() instanceof VMStartException) {
                throw (VMStartException) e.getCause();
            } else if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else {
                throw new IllegalStateException("Unexpected exception thrown when launching VM", e.getCause());
            }
        } catch (InterruptedException e) {
            throw new VMStartException("VM start interrupted", process);
        }
    }

    private String streamToString(final InputStream inputStream) {
        try {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            return null;
        }
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
