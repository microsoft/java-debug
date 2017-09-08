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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.ls.debug.adapter.AdapterUtils;
import org.eclipse.jdt.ls.debug.adapter.ErrorCode;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.Requests.SetExceptionBreakpointsArguments;
import org.eclipse.jdt.ls.debug.adapter.Types;

public class SetExceptionBreakpointsRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SETEXCEPTIONBREAKPOINTS);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        String[] filters = ((SetExceptionBreakpointsArguments) arguments).filters;
        try {
            boolean notifyCaught = ArrayUtils.contains(filters, Types.ExceptionBreakpointFilter.CAUGHT_EXCEPTION_FILTER_NAME);
            boolean notifyUncaught = ArrayUtils.contains(filters, Types.ExceptionBreakpointFilter.UNCAUGHT_EXCEPTION_FILTER_NAME);

            context.getDebugSession().setExceptionBreakpoints(notifyCaught, notifyUncaught);
        } catch (Exception ex) {
            AdapterUtils.setErrorResponse(response, ErrorCode.SET_EXCEPTIONBREAKPOINT_FAILURE,
                    String.format("Failed to setExceptionBreakpoints. Reason: '%s'", ex.toString()));
        }
    }

}
