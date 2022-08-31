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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.StepRequest;

public class DebugUtility {
    public static final String HOME = "home";
    public static final String OPTIONS = "options";
    public static final String MAIN = "main";
    public static final String SUSPEND = "suspend";
    public static final String QUOTE = "quote";
    public static final String EXEC = "vmexec";
    public static final String CWD = "cwd";
    public static final String ENV = "env";
    public static final String HOSTNAME = "hostname";
    public static final String PORT = "port";
    public static final String TIMEOUT = "timeout";

    /**
     * Launch a debuggee in suspend mode.
     * @see #launch(VirtualMachineManager, String, String, String, String, String, String, String[])
     */
    public static IDebugSession launch(VirtualMachineManager vmManager,
            String mainClass,
            String programArguments,
            String vmArguments,
            List<String> modulePaths,
            List<String> classPaths,
            String cwd,
            String[] envVars)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        return DebugUtility.launch(vmManager,
                mainClass,
                programArguments,
                vmArguments,
                String.join(File.pathSeparator, modulePaths),
                String.join(File.pathSeparator, classPaths),
                cwd,
                envVars);
    }

    /**
     * Launch a debuggee in suspend mode.
     * @see #launch(VirtualMachineManager, String, String, String, String, String, String, String[], String)
     */
    public static IDebugSession launch(VirtualMachineManager vmManager,
            String mainClass,
            String programArguments,
            String vmArguments,
            List<String> modulePaths,
            List<String> classPaths,
            String cwd,
            String[] envVars,
            String javaExec)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        return DebugUtility.launch(vmManager,
                mainClass,
                programArguments,
                vmArguments,
                String.join(File.pathSeparator, modulePaths),
                String.join(File.pathSeparator, classPaths),
                cwd,
                envVars,
                javaExec);
    }

    /**
     * Launches a debuggee in suspend mode.
     *
     * @param vmManager
     *            the virtual machine manager.
     * @param mainClass
     *            the main class.
     * @param programArguments
     *            the program arguments.
     * @param vmArguments
     *            the vm arguments.
     * @param modulePaths
     *            the module paths.
     * @param classPaths
     *            the class paths.
     * @param cwd
     *            the working directory of the program.
     * @param envVars
     *            array of strings, each element of which has environment variable settings in the format name=value.
     *            or null if the subprocess should inherit the environment of the current process.
     * @return an instance of IDebugSession.
     * @throws IOException
     *             when unable to launch.
     * @throws IllegalConnectorArgumentsException
     *             when one of the arguments is invalid.
     * @throws VMStartException
     *             when the debuggee was successfully launched, but terminated
     *             with an error before a connection could be established.
     */
    public static IDebugSession launch(VirtualMachineManager vmManager,
            String mainClass,
            String programArguments,
            String vmArguments,
            String modulePaths,
            String classPaths,
            String cwd,
            String[] envVars)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        return launch(vmManager, mainClass, programArguments, vmArguments, modulePaths, classPaths, cwd, envVars, null);
    }

    /**
     * Launches a debuggee in suspend mode.
     *
     * @param vmManager
     *            the virtual machine manager.
     * @param mainClass
     *            the main class.
     * @param programArguments
     *            the program arguments.
     * @param vmArguments
     *            the vm arguments.
     * @param modulePaths
     *            the module paths.
     * @param classPaths
     *            the class paths.
     * @param cwd
     *            the working directory of the program.
     * @param envVars
     *            array of strings, each element of which has environment variable settings in the format name=value.
     *            or null if the subprocess should inherit the environment of the current process.
     * @param javaExec
     *            the java executable path. If not defined, then resolve from java home.
     * @return an instance of IDebugSession.
     * @throws IOException
     *             when unable to launch.
     * @throws IllegalConnectorArgumentsException
     *             when one of the arguments is invalid.
     * @throws VMStartException
     *             when the debuggee was successfully launched, but terminated
     *             with an error before a connection could be established.
     */
    public static IDebugSession launch(VirtualMachineManager vmManager,
            String mainClass,
            String programArguments,
            String vmArguments,
            String modulePaths,
            String classPaths,
            String cwd,
            String[] envVars,
            String javaExec)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        List<LaunchingConnector> connectors = vmManager.launchingConnectors();
        LaunchingConnector connector = connectors.get(0);

        /** In the sun JDK 10, the first launching connector is com.sun.tools.jdi.RawCommandLineLauncher, which is not the one we want to use.
         *  Add the logic to filter the right one from LaunchingConnector list.
         *  This fix is only for the JDI implementation by JDK. Other JDI implementations (such as JDT) doesn't have the impact.
         */
        final String SUN_LAUNCHING_CONNECTOR = "com.sun.tools.jdi.SunCommandLineLauncher";
        for (LaunchingConnector con : connectors) {
            if (con.getClass().getName().equals(SUN_LAUNCHING_CONNECTOR)) {
                connector = con;
                break;
            }
        }

        Map<String, Argument> arguments = connector.defaultArguments();
        arguments.get(SUSPEND).setValue("true");

        String options = "";
        if (StringUtils.isNotBlank(vmArguments)) {
            options = vmArguments;
        }
        if (StringUtils.isNotBlank(modulePaths)) {
            options += " --module-path \"" + modulePaths + "\"";
        }
        if (StringUtils.isNotBlank(classPaths)) {
            options += " -cp \"" + classPaths + "\"";
        }
        arguments.get(OPTIONS).setValue(options);

        // For java 9 project, should specify "-m $MainClass".
        String[] mainClasses = mainClass.split("/");
        if (mainClasses.length == 2) {
            mainClass = "-m " + mainClass;
        }
        if (StringUtils.isNotBlank(programArguments)) {
            mainClass += " " + programArguments;
        }
        arguments.get(MAIN).setValue(mainClass);

        if (arguments.get(CWD) != null) {
            arguments.get(CWD).setValue(cwd);
        }

        if (arguments.get(ENV) != null) {
            arguments.get(ENV).setValue(encodeArrayArgument(envVars));
        }

        if (isValidJavaExec(javaExec)) {
            String vmExec = new File(javaExec).getName();
            String javaHome = new File(javaExec).getParentFile().getParentFile().getAbsolutePath();
            arguments.get(HOME).setValue(javaHome);
            arguments.get(EXEC).setValue(vmExec);
        } else if (StringUtils.isNotEmpty(DebugSettings.getCurrent().javaHome)) {
            arguments.get(HOME).setValue(DebugSettings.getCurrent().javaHome);
        }

        VirtualMachine vm = connector.launch(arguments);
        // workaround for JDT bug.
        // vm.version() calls org.eclipse.jdi.internal.MirrorImpl#requestVM
        // It calls vm.getIDSizes() to read related sizes including ReferenceTypeIdSize,
        // which is required to construct requests with null ReferenceType (such as ExceptionRequest)
        // Without this line, it throws ObjectCollectedException in ExceptionRequest.enable().
        // See https://github.com/Microsoft/java-debug/issues/23
        vm.version();
        return new DebugSession(vm);
    }

    private static boolean isValidJavaExec(String javaExec) {
        if (StringUtils.isBlank(javaExec)) {
            return false;
        }

        File file = new File(javaExec);
        if (!file.exists() || !file.isFile()) {
            return false;
        }

        return Files.isExecutable(file.toPath())
            && Objects.equals(file.getParentFile().getName(), "bin");
    }

    /**
     * Attach to an existing debuggee VM.
     * @param vmManager
     *               the virtual machine manager
     * @param hostName
     *               the machine where the debuggee VM is launched on
     * @param port
     *               the debug port that the debuggee VM exposed
     * @param attachTimeout
     *               the timeout when attaching to the debuggee VM
     * @return an instance of IDebugSession
     * @throws IOException
     *               when unable to attach.
     * @throws IllegalConnectorArgumentsException
     *               when one of the connector arguments is invalid.
     */
    public static IDebugSession attach(VirtualMachineManager vmManager, String hostName, int port, int attachTimeout)
            throws IOException, IllegalConnectorArgumentsException {
        List<AttachingConnector> connectors = vmManager.attachingConnectors();
        AttachingConnector connector = connectors.get(0);
        // in JDK 10, the first AttachingConnector is not the one we want
        final String SUN_ATTACH_CONNECTOR = "com.sun.tools.jdi.SocketAttachingConnector";
        for (AttachingConnector con : connectors) {
            if (con.getClass().getName().equals(SUN_ATTACH_CONNECTOR)) {
                connector = con;
                break;
            }
        }
        Map<String, Argument> arguments = connector.defaultArguments();
        arguments.get(HOSTNAME).setValue(hostName);
        arguments.get(PORT).setValue(String.valueOf(port));
        arguments.get(TIMEOUT).setValue(String.valueOf(attachTimeout));
        return new DebugSession(connector.attach(arguments));
    }

    /**
     * Create a step over request on the specified thread.
     * @param thread
     *              the target thread.
     * @param stepFilters
     *              the step filters when stepping.
     * @return the new step request.
     */
    public static StepRequest createStepOverRequest(ThreadReference thread, String[] stepFilters) {
        return createStepOverRequest(thread, null, stepFilters);
    }

    /**
     * Create a step over request on the specified thread.
     * @param thread
     *              the target thread.
     * @param classFilters
     *              restricts the step event to those matching the given class patterns when stepping.
     * @param classExclusionFilters
     *              restricts the step event to those not matching the given class patterns when stepping.
     * @return the new step request.
     */
    public static StepRequest createStepOverRequest(ThreadReference thread, String[] classFilters, String[] classExclusionFilters) {
        return createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_OVER, classFilters, classExclusionFilters);
    }

    /**
     * Create a step into request on the specified thread.
     * @param thread
     *              the target thread.
     * @param stepFilters
     *              the step filters when stepping.
     * @return the new step request.
     */
    public static StepRequest createStepIntoRequest(ThreadReference thread, String[] stepFilters) {
        return createStepIntoRequest(thread, null, stepFilters);
    }

    /**
     * Create a step into request on the specified thread.
     * @param thread
     *              the target thread.
     * @param classFilters
     *              restricts the step event to those matching the given class patterns when stepping.
     * @param classExclusionFilters
     *              restricts the step event to those not matching the given class patterns when stepping.
     * @return the new step request.
     */
    public static StepRequest createStepIntoRequest(ThreadReference thread, String[] classFilters, String[] classExclusionFilters) {
        return createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO, classFilters, classExclusionFilters);
    }

    /**
     * Create a step out request on the specified thread.
     * @param thread
     *              the target thread.
     * @param stepFilters
     *              the step filters when stepping.
     * @return the new step request.
     */
    public static StepRequest createStepOutRequest(ThreadReference thread, String[] stepFilters) {
        return createStepOutRequest(thread, null, stepFilters);
    }

    /**
     * Create a step out request on the specified thread.
     * @param thread
     *              the target thread.
     * @param classFilters
     *              restricts the step event to those matching the given class patterns when stepping.
     * @param classExclusionFilters
     *              restricts the step event to those not matching the given class patterns when stepping.
     * @return the new step request.
     */
    public static StepRequest createStepOutRequest(ThreadReference thread, String[] classFilters, String[] classExclusionFilters) {
        return createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_OUT, classFilters, classExclusionFilters);
    }

    private static StepRequest createStepRequest(ThreadReference thread, int stepSize, int stepDepth, String[] classFilters, String[] classExclusionFilters) {
        StepRequest request = thread.virtualMachine().eventRequestManager().createStepRequest(thread, stepSize, stepDepth);
        if (classFilters != null) {
            for (String classFilter : classFilters) {
                request.addClassFilter(classFilter);
            }
        }
        if (classExclusionFilters != null) {
            for (String exclusionFilter : classExclusionFilters) {
                request.addClassExclusionFilter(exclusionFilter);
            }
        }
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.addCountFilter(1);

        return request;
    }

    /**
     * Suspend the main thread when the program enters the main method of the specified main class.
     * @param debugSession
     *                  the debug session.
     * @param mainClass
     *                  the fully qualified name of the main class.
     * @return
     *        a {@link CompletableFuture} that contains the suspended main thread id.
     */
    public static CompletableFuture<Long> stopOnEntry(IDebugSession debugSession, String mainClass) {
        CompletableFuture<Long> future = new CompletableFuture<>();

        EventRequestManager manager = debugSession.getVM().eventRequestManager();
        MethodEntryRequest request = manager.createMethodEntryRequest();
        request.addClassFilter(mainClass);
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);

        debugSession.getEventHub().events().filter(debugEvent -> {
            return debugEvent.event instanceof MethodEntryEvent && request.equals(debugEvent.event.request());
        }).subscribe(debugEvent -> {
            Method method = ((MethodEntryEvent) debugEvent.event).method();
            if (method.isPublic() && method.isStatic() && method.name().equals("main")
                    && method.signature().equals("([Ljava/lang/String;)V")) {
                deleteEventRequestSafely(debugSession.getVM().eventRequestManager(), request);
                debugEvent.shouldResume = false;
                ThreadReference bpThread = ((MethodEntryEvent) debugEvent.event).thread();
                future.complete(bpThread.uniqueID());
            }
        });
        request.enable();

        return future;
    }

    /**
     * Get the ThreadReference instance by the thread id.
     * @param debugSession
     *                  the debug session
     * @param threadId
     *                  the thread id
     * @return the ThreadReference instance
     */
    public static ThreadReference getThread(IDebugSession debugSession, long threadId) {
        for (ThreadReference thread : getAllThreadsSafely(debugSession)) {
            if (thread.uniqueID() == threadId) {
                return thread;
            }
        }
        return null;
    }

    /**
     * Get the available ThreadReference list in the debug session.
     * If the debug session has terminated, return an empty list instead of VMDisconnectedException.
     * @param debugSession
     *                  the debug session
     * @return the available ThreadReference list
     */
    public static List<ThreadReference> getAllThreadsSafely(IDebugSession debugSession) {
        if (debugSession != null) {
            try {
                return debugSession.getAllThreads();
            } catch (VMDisconnectedException ex) {
                // do nothing.
            }
        }
        return new ArrayList<>();
    }

    /**
     * Resume the thread the times as it has been suspended.
     *
     * @param thread
     *              the thread reference
     */
    public static void resumeThread(ThreadReference thread) {
        // if thread is not found or is garbage collected, do nothing
        if (thread == null) {
            return;
        }
        try {
            int suspends = thread.suspendCount();
            for (int i = 0; i < suspends; i++) {
                /**
                 * Invoking this method will decrement the count of pending suspends on this thread.
                 * If it is decremented to 0, the thread will continue to execute.
                 */
                thread.resume();
            }
        } catch (ObjectCollectedException ex) {
            // ObjectCollectionException can be thrown if the thread has already completed (exited) in the VM when calling suspendCount,
            // the resume operation to this thread is meanness.
        }
    }

    public static void resumeThread(ThreadReference thread, int resumeCount) {
        if (thread == null) {
            return;
        }

        try {
            for (int i = 0; i < resumeCount; i++) {
                /**
                 * Invoking this method will decrement the count of pending suspends on this thread.
                 * If it is decremented to 0, the thread will continue to execute.
                 */
                thread.resume();
            }
        } catch (ObjectCollectedException ex) {
            // ObjectCollectionException can be thrown if the thread has already completed (exited) in the VM when calling suspendCount,
            // the resume operation to this thread is meanness.
        }
    }

    /**
     * Remove the event request from the vm. If the vm has terminated, do nothing.
     * @param eventManager
     *                  The event request manager.
     * @param request
     *                  The target event request.
     */
    public static void deleteEventRequestSafely(EventRequestManager eventManager, EventRequest request) {
        try {
            eventManager.deleteEventRequest(request);
        } catch (VMDisconnectedException ex) {
            // ignore.
        }
    }

    /**
     * Remove the event request list from the vm. If the vm has terminated, do nothing.
     * @param eventManager
     *                  The event request manager.
     * @param requests
     *                  The target event request list.
     */
    public static void deleteEventRequestSafely(EventRequestManager eventManager, List<EventRequest> requests) {
        try {
            eventManager.deleteEventRequests(requests);
        } catch (VMDisconnectedException ex) {
            // ignore.
        }
    }

    /**
     * Encode an string array to a string as the follows.
     *
     * <p>source argument:
     * <pre>["path=C:\\ProgramFiles\\java\\bin", "JAVA_HOME=C:\\ProgramFiles\\java"]</pre>
     *
     * <p>after encoded:
     * <pre>"path%3DC%3A%5CProgramFiles%5Cjava%5Cbin\nJAVA_HOME%3DC%3A%5CProgramFiles%5Cjava"</pre>
     *
     * @param argument the string array arguments
     * @return the encoded string
     */
    public static String encodeArrayArgument(String[] argument) {
        if (argument == null) {
            return null;
        }

        List<String> encodedArgs = new ArrayList<>();
        for (String arg : argument) {
            try {
                encodedArgs.add(URLEncoder.encode(arg, StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException e) {
                // do nothing.
            }
        }
        return String.join("\n", encodedArgs);
    }

    /**
     * Decode the encoded string to the original string array by the rules defined in encodeArrayArgument.
     *
     * @param argument the encoded string
     * @return the original string array argument
     */
    public static String[] decodeArrayArgument(String argument) {
        if (argument == null) {
            return null;
        }

        List<String> result = new ArrayList<>();
        String[] splits = argument.split("\n");
        for (String split : splits) {
            try {
                result.add(URLDecoder.decode(split, StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException e) {
                // do nothing.
            }
        }

        return result.toArray(new String[0]);
    }

    /**
     * Parses the given command line into separate arguments that can be passed
     * to <code>Runtime.getRuntime().exec(cmdArray)</code>.
     *
     * @param cmdStr command line as a single string.
     * @return the individual arguments.
     */
    public static List<String> parseArguments(String cmdStr) {
        if (cmdStr == null) {
            return new ArrayList<>();
        }

        return AdapterUtils.isWindows() ? parseArgumentsWindows(cmdStr) : parseArgumentsNonWindows(cmdStr);
    }

    /**
     * Parses the given command line into separate arguments for mac/linux platform.
     * This piece of code is mainly copied from
     * https://github.com/eclipse/eclipse.platform.debug/blob/master/org.eclipse.debug.core/core/org/eclipse/debug/core/DebugPlugin.java#L1374
     *
     * @param args
     *            the command line arguments as a single string.
     * @return the individual arguments
     */
    private static List<String> parseArgumentsNonWindows(String args) {
        // man sh, see topic QUOTING
        List<String> result = new ArrayList<>();

        final int DEFAULT = 0;
        final int ARG = 1;
        final int IN_DOUBLE_QUOTE = 2;
        final int IN_SINGLE_QUOTE = 3;

        int state = DEFAULT;
        StringBuilder buf = new StringBuilder();
        int len = args.length();
        for (int i = 0; i < len; i++) {
            char ch = args.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (state == DEFAULT) {
                    // skip
                    continue;
                } else if (state == ARG) {
                    state = DEFAULT;
                    result.add(buf.toString());
                    buf.setLength(0);
                    continue;
                }
            }
            switch (state) {
                case DEFAULT:
                case ARG:
                    if (ch == '"') {
                        state = IN_DOUBLE_QUOTE;
                    } else if (ch == '\'') {
                        state = IN_SINGLE_QUOTE;
                    } else if (ch == '\\' && i + 1 < len) {
                        state = ARG;
                        ch = args.charAt(++i);
                        buf.append(ch);
                    } else {
                        state = ARG;
                        buf.append(ch);
                    }
                    break;

                case IN_DOUBLE_QUOTE:
                    if (ch == '"') {
                        state = ARG;
                    } else if (ch == '\\' && i + 1 < len && (args.charAt(i + 1) == '\\' || args.charAt(i + 1) == '"')) {
                        ch = args.charAt(++i);
                        buf.append(ch);
                    } else {
                        buf.append(ch);
                    }
                    break;

                case IN_SINGLE_QUOTE:
                    if (ch == '\'') {
                        state = ARG;
                    } else {
                        buf.append(ch);
                    }
                    break;

                default:
                    throw new IllegalStateException();
            }
        }
        if (buf.length() > 0 || state != DEFAULT) {
            result.add(buf.toString());
        }

        return result;
    }

    /**
     * Parses the given command line into separate arguments for windows platform.
     * This piece of code is mainly copied from
     * https://github.com/eclipse/eclipse.platform.debug/blob/master/org.eclipse.debug.core/core/org/eclipse/debug/core/DebugPlugin.java#L1264
     *
     * @param args
     *            the command line arguments as a single string.
     * @return the individual arguments
     */
    private static List<String> parseArgumentsWindows(String args) {
        // see http://msdn.microsoft.com/en-us/library/a1y7w461.aspx
        List<String> result = new ArrayList<>();
        final int DEFAULT = 0;
        final int ARG = 1;
        final int IN_DOUBLE_QUOTE = 2;

        int state = DEFAULT;
        int backslashes = 0;
        StringBuilder buf = new StringBuilder();
        int len = args.length();
        for (int i = 0; i < len; i++) {
            char ch = args.charAt(i);
            if (ch == '\\') {
                backslashes++;
                continue;
            } else if (backslashes != 0) {
                if (ch == '"') {
                    for (; backslashes >= 2; backslashes -= 2) {
                        buf.append('\\');
                    }
                    if (backslashes == 1) {
                        if (state == DEFAULT) {
                            state = ARG;
                        }
                        buf.append('"');
                        backslashes = 0;
                        continue;
                    } // else fall through to switch
                } else {
                    // false alarm, treat passed backslashes literally...
                    if (state == DEFAULT) {
                        state = ARG;
                    }
                    for (; backslashes > 0; backslashes--) {
                        buf.append('\\');
                    }
                    // fall through to switch
                }
            }
            if (Character.isWhitespace(ch)) {
                if (state == DEFAULT) {
                    // skip
                    continue;
                } else if (state == ARG) {
                    state = DEFAULT;
                    result.add(buf.toString());
                    buf.setLength(0);
                    continue;
                }
            }
            switch (state) {
                case DEFAULT:
                case ARG:
                    if (ch == '"') {
                        state = IN_DOUBLE_QUOTE;
                    } else {
                        state = ARG;
                        buf.append(ch);
                    }
                    break;

                case IN_DOUBLE_QUOTE:
                    if (ch == '"') {
                        if (i + 1 < len && args.charAt(i + 1) == '"') {
                            /* Undocumented feature in Windows:
                             * Two consecutive double quotes inside a double-quoted argument are interpreted as
                             * a single double quote.
                             */
                            buf.append('"');
                            i++;
                        } else if (buf.length() == 0) {
                            // empty string on Windows platform. Account for bug in constructor of JDK's java.lang.ProcessImpl.
                            result.add("\"\""); //$NON-NLS-1$
                            state = DEFAULT;
                        } else {
                            state = ARG;
                        }
                    } else {
                        buf.append(ch);
                    }
                    break;

                default:
                    throw new IllegalStateException();
            }
        }
        if (buf.length() > 0 || state != DEFAULT) {
            result.add(buf.toString());
        }
        return result;
    }
}
