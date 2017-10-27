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

package com.microsoft.java.debug.core;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.jdi.BooleanValue;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LongValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ShortValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadGroupReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.VoidValue;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.EventRequestManager;

public class VirtualMachineLauncher {
    public static final String HOME = "home";
    public static final String OPTIONS = "options";
    public static final String MAIN = "main";
    public static final String SUSPEND = "suspend";
    public static final String QUOTE = "quote";
    public static final String EXEC = "exec";

    private static final int ACCEPT_TIMEOUT = 10 * 1000;
    private ListeningConnector listenConnector;

    public VirtualMachineLauncher(VirtualMachineManager vmManager) {
        List<ListeningConnector> connectors = vmManager.listeningConnectors();
        this.listenConnector = connectors.get(0);
    }

    /**
     * Return the default arguments.
     */
    public Map<String, String> defaultArguments() {
        HashMap<String, String> arguments = new HashMap<>(8);

        arguments.put(HOME, System.getProperty("java.home"));
        arguments.put(OPTIONS, "");
        arguments.put(MAIN, "");
        arguments.put(SUSPEND, "true");
        arguments.put(QUOTE, "\"");
        arguments.put(EXEC, "java");
        return arguments;
    }

    /**
     * Launch a virtual machine with specific configurations.
     */
    public VirtualMachine launch(Map<String, String> launchingOptions, String cwd, String[] envVars)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        if (listenConnector == null) {
            return null;
        }

        File workingDir = null;
        if (cwd != null && Files.isDirectory(Paths.get(cwd))) {
            workingDir = new File(cwd);
        }

        Map<String, Connector.Argument> args = listenConnector.defaultArguments();
        ((Connector.IntegerArgument) args.get("timeout")).setValue(ACCEPT_TIMEOUT);
        args.get("port").setValue(String.valueOf(findFreePort()));
        String address = listenConnector.startListening(args);

        String[] cmds = constructLaunchCommand(launchingOptions, address);
        Process process = Runtime.getRuntime().exec(cmds, envVars, workingDir);

        VirtualMachine vm;
        try {
            vm = listenConnector.accept(args);
        } catch (IOException | IllegalConnectorArgumentsException e) {
            process.destroy();
            throw new VMStartException(String.format("VM did not connect within given time: %d ms", ACCEPT_TIMEOUT), process);
        }

        return new VirtualMachineAdapter(vm, process);
    }

    private static String[] constructLaunchCommand(Map<String, String> launchingOptions, String address) {
        String javaHome = launchingOptions.get(HOME);
        String javaOptions = launchingOptions.get(OPTIONS);
        String main = launchingOptions.get(MAIN);
        boolean suspend = Boolean.valueOf(launchingOptions.get(SUSPEND));
        String javaExec = launchingOptions.get(EXEC);
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

    /**
     * Return a free port.
     */
    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            // do nothing.
        }
        return 0;
    }

    class VirtualMachineAdapter implements VirtualMachine {
        private VirtualMachine adaptee;
        private Process process;

        public VirtualMachineAdapter(VirtualMachine adaptee, Process process) {
            this.adaptee = adaptee;
            this.process = process;
        }

        @Override
        public VirtualMachine virtualMachine() {
            return adaptee;
        }

        @Override
        public List<ReferenceType> allClasses() {
            return adaptee.allClasses();
        }

        @Override
        public List<ThreadReference> allThreads() {
            return adaptee.allThreads();
        }

        @Override
        public boolean canAddMethod() {
            return adaptee.canAddMethod();
        }

        @Override
        public boolean canBeModified() {
            return adaptee.canBeModified();
        }

        @Override
        public boolean canForceEarlyReturn() {
            return adaptee.canForceEarlyReturn();
        }

        @Override
        public boolean canGetBytecodes() {
            return adaptee.canGetBytecodes();
        }

        @Override
        public boolean canGetClassFileVersion() {
            return adaptee.canGetClassFileVersion();
        }

        @Override
        public boolean canGetConstantPool() {
            return adaptee.canGetConstantPool();
        }

        @Override
        public boolean canGetCurrentContendedMonitor() {
            return adaptee.canGetCurrentContendedMonitor();
        }

        @Override
        public boolean canGetInstanceInfo() {
            return adaptee.canGetInstanceInfo();
        }

        @Override
        public boolean canGetMethodReturnValues() {
            return adaptee.canGetMethodReturnValues();
        }

        @Override
        public boolean canGetMonitorFrameInfo() {
            return adaptee.canGetMonitorFrameInfo();
        }

        @Override
        public boolean canGetMonitorInfo() {
            return adaptee.canGetMonitorInfo();
        }

        @Override
        public boolean canGetOwnedMonitorInfo() {
            return adaptee.canGetOwnedMonitorInfo();
        }

        @Override
        public boolean canGetSourceDebugExtension() {
            return adaptee.canGetSourceDebugExtension();
        }

        @Override
        public boolean canGetSyntheticAttribute() {
            return adaptee.canGetSyntheticAttribute();
        }

        @Override
        public boolean canPopFrames() {
            return adaptee.canPopFrames();
        }

        @Override
        public boolean canRedefineClasses() {
            return adaptee.canRedefineClasses();
        }

        @Override
        public boolean canRequestMonitorEvents() {
            return adaptee.canRequestMonitorEvents();
        }

        @Override
        public boolean canRequestVMDeathEvent() {
            return adaptee.canRequestVMDeathEvent();
        }

        @Override
        public boolean canUnrestrictedlyRedefineClasses() {
            return adaptee.canUnrestrictedlyRedefineClasses();
        }

        @Override
        public boolean canUseInstanceFilters() {
            return adaptee.canUseInstanceFilters();
        }

        @Override
        public boolean canUseSourceNameFilters() {
            return adaptee.canUseSourceNameFilters();
        }

        @Override
        public boolean canWatchFieldAccess() {
            return adaptee.canWatchFieldAccess();
        }

        @Override
        public boolean canWatchFieldModification() {
            return adaptee.canWatchFieldModification();
        }

        @Override
        public List<ReferenceType> classesByName(String arg0) {
            return adaptee.classesByName(arg0);
        }

        @Override
        public String description() {
            return adaptee.description();
        }

        @Override
        public void dispose() {
            adaptee.dispose();
        }

        @Override
        public EventQueue eventQueue() {
            return adaptee.eventQueue();
        }

        @Override
        public EventRequestManager eventRequestManager() {
            return adaptee.eventRequestManager();
        }

        @Override
        public void exit(int arg0) {
            adaptee.exit(arg0);
        }

        @Override
        public String getDefaultStratum() {
            return adaptee.getDefaultStratum();
        }

        @Override
        public long[] instanceCounts(List<? extends ReferenceType> arg0) {
            return adaptee.instanceCounts(arg0);
        }

        @Override
        public BooleanValue mirrorOf(boolean arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public ByteValue mirrorOf(byte arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public CharValue mirrorOf(char arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public ShortValue mirrorOf(short arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public IntegerValue mirrorOf(int arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public LongValue mirrorOf(long arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public FloatValue mirrorOf(float arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public DoubleValue mirrorOf(double arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public StringReference mirrorOf(String arg0) {
            return adaptee.mirrorOf(arg0);
        }

        @Override
        public VoidValue mirrorOfVoid() {
            return adaptee.mirrorOfVoid();
        }

        @Override
        public String name() {
            return adaptee.name();
        }

        @Override
        public Process process() {
            return process;
        }

        @Override
        public void redefineClasses(Map<? extends ReferenceType, byte[]> arg0) {
            adaptee.redefineClasses(arg0);
        }

        @Override
        public void resume() {
            adaptee.resume();
        }

        @Override
        public void setDebugTraceMode(int arg0) {
            adaptee.setDebugTraceMode(arg0);
        }

        @Override
        public void setDefaultStratum(String arg0) {
            adaptee.setDefaultStratum(arg0);
        }

        @Override
        public void suspend() {
            adaptee.suspend();
        }

        @Override
        public List<ThreadGroupReference> topLevelThreadGroups() {
            return adaptee.topLevelThreadGroups();
        }

        @Override
        public String version() {
            return adaptee.version();
        }

    }
}
