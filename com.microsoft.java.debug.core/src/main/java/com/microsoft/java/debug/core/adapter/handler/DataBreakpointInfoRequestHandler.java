/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.DataBreakpointInfoArguments;
import com.microsoft.java.debug.core.protocol.Responses.DataBreakpointInfoResponseBody;
import com.microsoft.java.debug.core.protocol.Types.DataBreakpointAccessType;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;

public class DataBreakpointInfoRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.DATABREAKPOINTINFO);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        DataBreakpointInfoArguments dataBpArgs = (DataBreakpointInfoArguments) arguments;
        if (dataBpArgs.variablesReference > 0) {
            Object container = context.getRecyclableIdPool().getObjectById(dataBpArgs.variablesReference);
            if (container instanceof VariableProxy) {
                if (!(((VariableProxy) container).getProxiedVariable() instanceof StackFrameReference)) {
                    ObjectReference containerObj = (ObjectReference) ((VariableProxy) container).getProxiedVariable();
                    ReferenceType type = containerObj.referenceType();
                    Field field = type.fieldByName(dataBpArgs.name);
                    if (field != null) {
                        String fullyQualifiedName = type.name();
                        String dataId = String.format("%s#%s", fullyQualifiedName, dataBpArgs.name);
                        String description = String.format("%s.%s : %s", getSimpleName(fullyQualifiedName), dataBpArgs.name, getSimpleName(field.typeName()));
                        response.body = new DataBreakpointInfoResponseBody(dataId, description,
                            DataBreakpointAccessType.values(), true);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(response);
    }

    private String getSimpleName(String typeName) {
        if (StringUtils.isBlank(typeName)) {
            return "";
        }

        String[] names = typeName.split("\\.");
        return names[names.length - 1];
    }
}
