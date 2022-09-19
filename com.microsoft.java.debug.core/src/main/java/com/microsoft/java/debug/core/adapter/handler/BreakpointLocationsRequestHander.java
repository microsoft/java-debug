/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.BreakpointLocationsArguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Types.BreakpointLocation;

/**
 * The breakpointLocations request returns all possible locations for source breakpoints in a given range.
 * Clients should only call this request if the corresponding capability supportsBreakpointLocationsRequest is true.
 */
public class BreakpointLocationsRequestHander implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Requests.Command.BREAKPOINTLOCATIONS);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        BreakpointLocationsArguments bpArgs = (BreakpointLocationsArguments) arguments;
        String sourceUri = SetBreakpointsRequestHandler.normalizeSourcePath(bpArgs.source, context);
        // When breakpoint source path is null or an invalid file path, send an ErrorResponse back.
        if (StringUtils.isBlank(sourceUri)) {
            throw AdapterUtils.createCompletionException(
                String.format("Failed to get BreakpointLocations. Reason: '%s' is an invalid path.", bpArgs.source.path),
                ErrorCode.SET_BREAKPOINT_FAILURE);
        }

        int debuggerLine = AdapterUtils.convertLineNumber(bpArgs.line, context.isClientLinesStartAt1(), context.isDebuggerLinesStartAt1());
        IBreakpoint[] breakpoints = context.getBreakpointManager().getBreakpoints(sourceUri);
        BreakpointLocation[] locations = new BreakpointLocation[0];
        for (int i = 0; i < breakpoints.length; i++) {
            if (breakpoints[i].getLineNumber() == debuggerLine && ArrayUtils.isNotEmpty(
                    breakpoints[i].sourceLocation().availableBreakpointLocations())) {
                locations = Stream.of(breakpoints[i].sourceLocation().availableBreakpointLocations()).map(location -> {
                    BreakpointLocation newLocaiton = new BreakpointLocation();
                    newLocaiton.line = AdapterUtils.convertLineNumber(location.line,
                                    context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
                    newLocaiton.column = AdapterUtils.convertColumnNumber(location.column,
                                    context.isDebuggerColumnsStartAt1(), context.isClientColumnsStartAt1());
                    newLocaiton.endLine = AdapterUtils.convertLineNumber(location.endLine,
                                    context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
                    newLocaiton.endColumn = AdapterUtils.convertColumnNumber(location.endColumn,
                                    context.isDebuggerColumnsStartAt1(), context.isClientColumnsStartAt1());
                    return newLocaiton;
                }).toArray(BreakpointLocation[]::new);
                break;
            }
        }

        response.body = new Responses.BreakpointLocationsResponseBody(locations);
        return CompletableFuture.completedFuture(response);
    }
}
