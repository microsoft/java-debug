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

package org.eclipse.jdt.ls.debug.adapter.handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.ls.debug.DebugUtility;
import org.eclipse.jdt.ls.debug.IDebugSession;
import org.eclipse.jdt.ls.debug.adapter.AdapterUtils;
import org.eclipse.jdt.ls.debug.adapter.Constants;
import org.eclipse.jdt.ls.debug.adapter.ErrorCode;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.ISourceLookUpProvider;
import org.eclipse.jdt.ls.debug.adapter.IVirtualMachineManagerProvider;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.AttachArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.internal.Logger;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;

public class AttachRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.ATTACH);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        AttachArguments attachArguments = (AttachArguments) arguments;
        context.setAttached(true);
        context.setSourcePath(attachArguments.sourcePath);

        IVirtualMachineManagerProvider vmProvider = context.getProvider(IVirtualMachineManagerProvider.class);
        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        if (attachArguments.projectName != null) {
            Map<String, Object> options = sourceProvider.getDefaultOptions();
            options.put(Constants.PROJECTNAME, attachArguments.projectName);
            sourceProvider.initialize(options);
        }

        try {
            Logger.logInfo(String.format("Trying to attach to remote debuggee VM %s:%d .",
                    attachArguments.hostName, attachArguments.port));
            IDebugSession debugSession = DebugUtility.attach(vmProvider.getVirtualMachineManager(),
                    attachArguments.hostName, attachArguments.port, attachArguments.attachTimeout);
            context.setDebugSession(debugSession);
            Logger.logInfo("Attaching to debuggee VM succeeded.");
        } catch (IOException | IllegalConnectorArgumentsException e) {
            AdapterUtils.setErrorResponse(response, ErrorCode.ATTACH_FAILURE,
                    String.format("Failed to attach to remote debuggee VM. Reason: %s", e.toString()));
        }
    }

}
