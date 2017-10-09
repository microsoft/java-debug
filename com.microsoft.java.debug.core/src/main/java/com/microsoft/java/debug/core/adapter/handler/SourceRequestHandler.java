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

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.Messages.Response;
import com.microsoft.java.debug.core.adapter.Requests.Arguments;
import com.microsoft.java.debug.core.adapter.Requests.Command;
import com.microsoft.java.debug.core.adapter.Requests.SourceArguments;
import com.microsoft.java.debug.core.adapter.Responses;

public class SourceRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SOURCE);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        int sourceReference = ((SourceArguments) arguments).sourceReference;
        if (sourceReference <= 0) {
            logger.severe("SourceRequest: property 'sourceReference' is missing, null, or empty");
            AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                    "SourceRequest: property 'sourceReference' is missing, null, or empty");
        } else {
            String uri = context.getSourceUri(sourceReference);
            ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
            response.body = new Responses.SourceResponseBody(sourceProvider.getSourceContents(uri));
        }
    }

}
