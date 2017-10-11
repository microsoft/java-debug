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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;

import com.sun.jdi.Location;
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
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.StepRequest;

public class DebugUtility {
    public static IDebugSession launch(VirtualMachineManager vmManager, String mainClass, String programArguments, String vmArguments, List<String> classPaths)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        return DebugUtility.launch(vmManager, mainClass, programArguments, vmArguments, String.join(File.pathSeparator, classPaths));
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
     * @param classPaths
     *            the class paths.
     * @return an instance of IDebugSession.
     * @throws IOException
     *             when unable to launch.
     * @throws IllegalConnectorArgumentsException
     *             when one of the arguments is invalid.
     * @throws VMStartException
     *             when the debuggee was successfully launched, but terminated
     *             with an error before a connection could be established.
     */
    public static IDebugSession launch(VirtualMachineManager vmManager, String mainClass, String programArguments, String vmArguments, String classPaths)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        List<LaunchingConnector> connectors = vmManager.launchingConnectors();
        LaunchingConnector connector = connectors.get(0);

        Map<String, Argument> arguments = connector.defaultArguments();
        arguments.get("suspend").setValue("true");
        if (StringUtils.isNotBlank(vmArguments)) {
            arguments.get("options").setValue(vmArguments + " -cp \"" + classPaths + "\"");
        } else {
            arguments.get("options").setValue("-cp \"" + classPaths + "\"");
        }
        if (StringUtils.isNotBlank(programArguments)) {
            arguments.get("main").setValue(mainClass + " " + programArguments);
        } else {
            arguments.get("main").setValue(mainClass);
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
        arguments.get("hostname").setValue(hostName);
        arguments.get("port").setValue(String.valueOf(port));
        arguments.get("timeout").setValue(String.valueOf(attachTimeout));
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
                    thread.virtualMachine().eventRequestManager().deleteEventRequest(request);
                });
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.addCountFilter(1);
        request.enable();

        thread.resume();

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
                return debugSession.allThreads();
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
}
