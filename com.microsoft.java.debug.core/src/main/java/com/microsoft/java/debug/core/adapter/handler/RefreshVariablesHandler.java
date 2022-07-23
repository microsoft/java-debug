/*******************************************************************************
* Copyright (c) 2021 Microsoft Corporation and others.
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

import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.formatter.NumericFormatEnum;
import com.microsoft.java.debug.core.protocol.Events.InvalidatedAreas;
import com.microsoft.java.debug.core.protocol.Events.InvalidatedEvent;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.RefreshVariablesArguments;

import static com.microsoft.java.debug.core.adapter.formatter.NumericFormatEnum.HEX;
import static com.microsoft.java.debug.core.adapter.formatter.NumericFormatEnum.DEC;

public class RefreshVariablesHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.REFRESHVARIABLES);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        RefreshVariablesArguments refreshArgs = (RefreshVariablesArguments) arguments;
        if (refreshArgs != null) {
            DebugSettings.getCurrent().formatType = getFormatType(refreshArgs.showHex, refreshArgs.formatType);
            DebugSettings.getCurrent().showQualifiedNames = refreshArgs.showQualifiedNames;
            DebugSettings.getCurrent().showStaticVariables = refreshArgs.showStaticVariables;
            DebugSettings.getCurrent().showLogicalStructure = refreshArgs.showLogicalStructure;
            DebugSettings.getCurrent().showToString = refreshArgs.showToString;
        }

        context.getProtocolServer().sendEvent(new InvalidatedEvent(InvalidatedAreas.VARIABLES));
        return CompletableFuture.completedFuture(response);
    }

    private NumericFormatEnum getFormatType(boolean showHex, String formatType) {
        if (formatType != null) {
            try {
                return NumericFormatEnum.valueOf(formatType);
            } catch (IllegalArgumentException exp) {
                // can't parse format so just return default value;
            }
        }
        return showHex ? NumericFormatEnum.HEX : NumericFormatEnum.DEC;
    }
}
