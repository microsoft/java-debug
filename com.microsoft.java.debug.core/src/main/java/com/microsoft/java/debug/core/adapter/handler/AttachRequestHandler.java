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
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.AttachArguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

public class AttachRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

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

        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);

        try {
            logger.info(String.format("Trying to attach to remote debuggee VM %s:%d .", attachArguments.hostName, attachArguments.port));
            IDebugSession debugSession = DebugUtility.attach(vmProvider.getVirtualMachineManager(), attachArguments.hostName, attachArguments.port,
                    attachArguments.timeout);
            context.setDebugSession(debugSession);
            logger.info("Attaching to debuggee VM succeeded.");

            // If the debugger and debuggee run at the different JVM platforms, show a warning message.
            if (debugSession != null) {
                String debuggeeVersion = debugSession.getVM().version();
                String debuggerVersion = System.getProperty("java.version");
                if (!debuggerVersion.equals(debuggeeVersion)) {
                    String warnMessage = String.format("[Warn] The debugger and the debuggee are running in different versions of JVMs. "
                        + "You could see wrong source mapping results.\n"
                        + "Debugger JVM version: %s\n"
                        + "Debuggee JVM version: %s", debuggerVersion, debuggeeVersion);
                    logger.warning(warnMessage);
                    context.sendEvent(Events.OutputEvent.createConsoleOutput(warnMessage));
                }
            }
        } catch (IOException | IllegalConnectorArgumentsException e) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.ATTACH_FAILURE,
                        String.format("Failed to attach to remote debuggee VM. Reason: %s", e.toString()));
        }

        Map<String, Object> options = new HashMap<>();
        options.put(Constants.DEBUGGEE_ENCODING, context.getDebuggeeEncoding());
        if (attachArguments.projectName != null) {
            options.put(Constants.PROJECTNAME, attachArguments.projectName);
        }
        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        sourceProvider.initialize(context.getDebugSession(), options);

        // Send an InitializedEvent to indicate that the debugger is ready to accept configuration requests
        // (e.g. SetBreakpointsRequest, SetExceptionBreakpointsRequest).
        context.sendEvent(new Events.InitializedEvent());
        return CompletableFuture.completedFuture(response);
    }

}
