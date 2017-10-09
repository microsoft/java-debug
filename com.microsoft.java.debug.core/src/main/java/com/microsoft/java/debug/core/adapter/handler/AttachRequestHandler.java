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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
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
import com.microsoft.java.debug.core.adapter.Messages.Response;
import com.microsoft.java.debug.core.adapter.Requests.Arguments;
import com.microsoft.java.debug.core.adapter.Requests.AttachArguments;
import com.microsoft.java.debug.core.adapter.Requests.Command;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

public class AttachRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.ATTACH);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        AttachArguments attachArguments = (AttachArguments) arguments;
        context.setAttached(true);
        context.setSourcePaths(attachArguments.sourcePaths);
        context.setDebuggeeEncoding(StandardCharsets.UTF_8); // Use UTF-8 as debuggee's default encoding format.

        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);
        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        Map<String, Object> options = sourceProvider.getDefaultOptions();
        options.put(Constants.DEBUGGEE_ENCODING, context.getDebuggeeEncoding());
        if (attachArguments.projectName != null) {
            options.put(Constants.PROJECTNAME, attachArguments.projectName);
        }
        sourceProvider.initialize(options);

        try {
            logger.info(String.format("Trying to attach to remote debuggee VM %s:%d .",
                    attachArguments.hostName, attachArguments.port));
            IDebugSession debugSession = DebugUtility.attach(vmProvider.getVirtualMachineManager(),
                    attachArguments.hostName, attachArguments.port, attachArguments.timeout);
            context.setDebugSession(debugSession);
            logger.info("Attaching to debuggee VM succeeded.");
        } catch (IOException | IllegalConnectorArgumentsException e) {
            logger.log(Level.SEVERE, String.format("Failed to attach debuggee VM: %s", e.toString()), e);
            AdapterUtils.setErrorResponse(response, ErrorCode.ATTACH_FAILURE,
                    String.format("Failed to attach to remote debuggee VM. Reason: %s", e.toString()));
        }
    }

}
