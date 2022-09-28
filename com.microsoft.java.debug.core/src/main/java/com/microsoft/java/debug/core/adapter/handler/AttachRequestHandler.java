/*******************************************************************************
* Copyright (c) 2017-2020 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.ICompletionsProvider;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.AttachArguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.request.EventRequest;

import org.apache.commons.lang3.StringUtils;

public class AttachRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private VMHandler vmHandler = new VMHandler();

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.ATTACH);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        AttachArguments attachArguments = (AttachArguments) arguments;
        context.setAttached(true);
        context.setSourcePaths(attachArguments.sourcePaths);
        context.setDebuggeeEncoding(StandardCharsets.UTF_8); // Use UTF-8 as debuggee's default encoding format.
        context.setStepFilters(attachArguments.stepFilters);
        context.setLocalDebugging(isLocalHost(attachArguments.hostName));

        Map<String, Object> traceInfo = new HashMap<>();
        traceInfo.put("localAttach", context.isLocalDebugging());
        traceInfo.put("asyncJDWP", context.asyncJDWP());

        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);
        vmHandler.setVmProvider(vmProvider);
        IDebugSession debugSession = null;
        try {
            try {
                logger.info(String.format("Trying to attach to remote debuggee VM %s:%d .", attachArguments.hostName, attachArguments.port));
                debugSession = DebugUtility.attach(vmProvider.getVirtualMachineManager(), attachArguments.hostName, attachArguments.port,
                        attachArguments.timeout);
                context.setDebugSession(debugSession);
                vmHandler.connectVirtualMachine(debugSession.getVM());
                logger.info("Attaching to debuggee VM succeeded.");
            } catch (IOException | IllegalConnectorArgumentsException e) {
                throw AdapterUtils.createCompletionException(
                    String.format("Failed to attach to remote debuggee VM. Reason: %s", e.toString()),
                    ErrorCode.ATTACH_FAILURE,
                    e);
            }

            Map<String, Object> options = new HashMap<>();
            options.put(Constants.DEBUGGEE_ENCODING, context.getDebuggeeEncoding());
            if (attachArguments.projectName != null) {
                options.put(Constants.PROJECT_NAME, attachArguments.projectName);
            }
            // TODO: Clean up the initialize mechanism
            ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
            sourceProvider.initialize(context, options);
            // If the debugger and debuggee run at the different JVM platforms, show a warning message.
            if (debugSession != null) {
                String debuggeeVersion = debugSession.getVM().version();
                String debuggerVersion = sourceProvider.getJavaRuntimeVersion(attachArguments.projectName);
                if (StringUtils.isNotBlank(debuggerVersion) && !debuggerVersion.equals(debuggeeVersion)) {
                    String warnMessage = String.format("[Warn] The debugger and the debuggee are running in different versions of JVMs. "
                        + "You could see wrong source mapping results.\n"
                        + "Debugger JVM version: %s\n"
                        + "Debuggee JVM version: %s", debuggerVersion, debuggeeVersion);
                    logger.warning(warnMessage);
                    context.getProtocolServer().sendEvent(Events.OutputEvent.createConsoleOutput(warnMessage));
                }

                EventRequest request = debugSession.getVM().eventRequestManager().createVMDeathRequest();
                request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
                long sent = System.currentTimeMillis();
                request.enable();
                long received = System.currentTimeMillis();
                long latency = received - sent;
                context.setJDWPLatency(latency);
                logger.info("Network latency for JDWP command: " + latency + "ms");
                traceInfo.put("networkLatency", latency);
            }

            IEvaluationProvider evaluationProvider = context.getProvider(IEvaluationProvider.class);
            evaluationProvider.initialize(context, options);
            IHotCodeReplaceProvider hcrProvider = context.getProvider(IHotCodeReplaceProvider.class);
            hcrProvider.initialize(context, options);
            ICompletionsProvider completionsProvider = context.getProvider(ICompletionsProvider.class);
            completionsProvider.initialize(context, options);
        } finally {
            UsageDataSession.recordInfo("attach debug info", traceInfo);
        }

        // Send an InitializedEvent to indicate that the debugger is ready to accept configuration requests
        // (e.g. SetBreakpointsRequest, SetExceptionBreakpointsRequest).
        context.getProtocolServer().sendEvent(new Events.InitializedEvent());
        return CompletableFuture.completedFuture(response);
    }

    private boolean isLocalHost(String hostName) {
        if (hostName == null || "localhost".equals(hostName) || "127.0.0.1".equals(hostName)) {
            return true;
        }

        // TODO: Check the host name of current computer as well.
        return false;
    }

}
