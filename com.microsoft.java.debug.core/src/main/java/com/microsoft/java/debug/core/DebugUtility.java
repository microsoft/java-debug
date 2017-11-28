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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;

import com.sun.jdi.Location;
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
import com.sun.jdi.event.StepEvent;
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
     * @see {@link #launch(VirtualMachineManager, String, String, String, String, String)}
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
        List<LaunchingConnector> connectors = vmManager.launchingConnectors();
        LaunchingConnector connector = connectors.get(0);

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
        if (StringUtils.isNotBlank(modulePaths) || mainClasses.length == 2) {
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
        Map<String, Argument> arguments = connector.defaultArguments();
        arguments.get(HOSTNAME).setValue(hostName);
        arguments.get(PORT).setValue(String.valueOf(port));
        arguments.get(TIMEOUT).setValue(String.valueOf(attachTimeout));
        return new DebugSession(connector.attach(arguments));
    }

    /**
     * Steps over newly pushed frames.
     *
     * @param thread
     *            the target thread.
     * @param eventHub
     *            the {@link IEventHub} instance.
     * @return the new {@link Location} of the execution flow of the specified
     *         thread.
     */
    public static CompletableFuture<Location> stepOver(ThreadReference thread, IEventHub eventHub) {
        return DebugUtility.step(thread, eventHub, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
    }

    /**
     * Steps into newly pushed frames.
     *
     * @param thread
     *            the target thread.
     * @param eventHub
     *            the {@link IEventHub} instance.
     * @return the new {@link Location} of the execution flow of the specified
     *         thread.
     */
    public static CompletableFuture<Location> stepInto(ThreadReference thread, IEventHub eventHub) {
        return DebugUtility.step(thread, eventHub, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
    }

    /**
     * Steps out of the current frame.
     *
     * @param thread
     *            the target thread.
     * @param eventHub
     *            the {@link IEventHub} instance.
     * @return the new {@link Location} of the execution flow of the specified
     *         thread.
     */
    public static CompletableFuture<Location> stepOut(ThreadReference thread, IEventHub eventHub) {
        return DebugUtility.step(thread, eventHub, StepRequest.STEP_LINE, StepRequest.STEP_OUT);
    }

    private static CompletableFuture<Location> step(ThreadReference thread, IEventHub eventHub, int stepSize,
            int stepDepth) {
        CompletableFuture<Location> future = new CompletableFuture<>();

        StepRequest request = thread.virtualMachine().eventRequestManager().createStepRequest(thread, stepSize,
                stepDepth);

        eventHub.stepEvents().filter(debugEvent -> request.equals(debugEvent.event.request())).take(1)
                .subscribe(debugEvent -> {
                    StepEvent event = (StepEvent) debugEvent.event;
                    future.complete(event.location());
                    deleteEventRequestSafely(thread.virtualMachine().eventRequestManager(), request);
                });
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.addCountFilter(1);
        request.enable();

        DebugUtility.resumeThread(thread);
        return future;
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
            if (thread.uniqueID() == threadId && !thread.isCollected()) {
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
        if (thread == null || thread.isCollected()) {
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
}
