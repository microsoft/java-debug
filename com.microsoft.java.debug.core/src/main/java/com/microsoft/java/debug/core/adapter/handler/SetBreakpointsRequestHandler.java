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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.BreakpointManager;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.SetBreakpointArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;

public class SetBreakpointsRequestHandler implements IDebugRequestHandler {

    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private BreakpointManager manager = new BreakpointManager();

    private boolean isHcrInitialized = false;

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SETBREAKPOINTS);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        // TODO: This part logic should be part of post launching handler.
        //       Add post launch logic and move the reinstall breakpoints logic there.
        if (!isHcrInitialized) {
            IHotCodeReplaceProvider hcrProvider = context.getProvider(IHotCodeReplaceProvider.class);
            hcrProvider.redefineClasses((typenames) -> {
                this.reinstallBreakpoints(context, typenames);
            });
            isHcrInitialized = true;
        }

        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Empty debug session.");
        }

        SetBreakpointArguments bpArguments = (SetBreakpointArguments) arguments;
        String clientPath = bpArguments.source.path;
        if (AdapterUtils.isWindows()) {
            // VSCode may send drive letters with inconsistent casing which will mess up the key
            // in the BreakpointManager. See https://github.com/Microsoft/vscode/issues/6268
            // Normalize the drive letter casing. Note that drive letters
            // are not localized so invariant is safe here.
            String drivePrefix = FilenameUtils.getPrefix(clientPath);
            if (drivePrefix != null && drivePrefix.length() >= 2
                    && Character.isLowerCase(drivePrefix.charAt(0)) && drivePrefix.charAt(1) == ':') {
                drivePrefix = drivePrefix.substring(0, 2); // d:\ is an illegal regex string, convert it to d:
                clientPath = clientPath.replaceFirst(drivePrefix, drivePrefix.toUpperCase());
            }
        }
        String sourcePath = clientPath;
        if (bpArguments.source.sourceReference != 0 && context.getSourceUri(bpArguments.source.sourceReference) != null) {
            sourcePath = context.getSourceUri(bpArguments.source.sourceReference);
        } else if (StringUtils.isNotBlank(clientPath)) {
            // See the bug https://github.com/Microsoft/vscode/issues/30996
            // Source.path in the SetBreakpointArguments could be a file system path or uri.
            sourcePath = AdapterUtils.convertPath(clientPath, AdapterUtils.isUri(clientPath), context.isDebuggerPathsAreUri());
        }

        // When breakpoint source path is null or an invalid file path, send an ErrorResponse back.
        if (StringUtils.isBlank(sourcePath)) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.SET_BREAKPOINT_FAILURE,
                        String.format("Failed to setBreakpoint. Reason: '%s' is an invalid path.", bpArguments.source.path));
        }

        try {
            List<Types.Breakpoint> res = new ArrayList<>();
            IBreakpoint[] toAdds = this.convertClientBreakpointsToDebugger(sourcePath, bpArguments.breakpoints, context);
            // See the VSCode bug https://github.com/Microsoft/vscode/issues/36471.
            // The source uri sometimes is encoded by VSCode, the debugger will decode it to keep the uri consistent.
            IBreakpoint[] added = manager.setBreakpoints(AdapterUtils.decodeURIComponent(sourcePath), toAdds, bpArguments.sourceModified);
            for (int i = 0; i < bpArguments.breakpoints.length; i++) {
                // For newly added breakpoint, should install it to debuggee first.
                if (toAdds[i] == added[i] && added[i].className() != null) {
                    added[i].install().thenAccept(bp -> {
                        Events.BreakpointEvent bpEvent = new Events.BreakpointEvent("new", this.convertDebuggerBreakpointToClient(bp, context));
                        context.getProtocolServer().sendEvent(bpEvent);
                    });
                } else if (toAdds[i].hitCount() != added[i].hitCount() && added[i].className() != null) {
                    // Update hitCount condition.
                    added[i].setHitCount(toAdds[i].hitCount());
                }
                res.add(this.convertDebuggerBreakpointToClient(added[i], context));
            }
            response.body = new Responses.SetBreakpointsResponseBody(res);
            return CompletableFuture.completedFuture(response);
        } catch (DebugException e) {
            return AdapterUtils.createAsyncErrorResponse(response,
                    ErrorCode.SET_BREAKPOINT_FAILURE,
                    String.format("Failed to setBreakpoint. Reason: '%s'", e.toString()));
        }
    }

    private Types.Breakpoint convertDebuggerBreakpointToClient(IBreakpoint breakpoint, IDebugAdapterContext context) {
        int id = (int) breakpoint.getProperty("id");
        boolean verified = breakpoint.getProperty("verified") != null && (boolean) breakpoint.getProperty("verified");
        int lineNumber = AdapterUtils.convertLineNumber(breakpoint.lineNumber(), context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
        return new Types.Breakpoint(id, verified, lineNumber, "");
    }

    private IBreakpoint[] convertClientBreakpointsToDebugger(String sourceFile, Types.SourceBreakpoint[] sourceBreakpoints, IDebugAdapterContext context)
            throws DebugException {
        int[] lines = Arrays.asList(sourceBreakpoints).stream().map(sourceBreakpoint -> {
            return AdapterUtils.convertLineNumber(sourceBreakpoint.line, context.isClientLinesStartAt1(), context.isDebuggerLinesStartAt1());
        }).mapToInt(line -> line).toArray();
        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        String[] fqns = sourceProvider.getFullyQualifiedName(sourceFile, lines, null);
        IBreakpoint[] breakpoints = new IBreakpoint[lines.length];
        for (int i = 0; i < lines.length; i++) {
            int hitCount = 0;
            try {
                hitCount = Integer.parseInt(sourceBreakpoints[i].hitCondition);
            } catch (NumberFormatException e) {
                hitCount = 0; // If hitCount is an illegal number, ignore hitCount condition.
            }
            breakpoints[i] = context.getDebugSession().createBreakpoint(fqns[i], lines[i], hitCount);
            if (sourceProvider.supportsRealtimeBreakpointVerification() && StringUtils.isNotBlank(fqns[i])) {
                breakpoints[i].putProperty("verified", true);
            }
        }
        return breakpoints;
    }

    private void reinstallBreakpoints(IDebugAdapterContext context, List<String> typenames) {
        if (typenames == null || typenames.isEmpty()) {
            return;
        }
        IBreakpoint[] breakpoints = manager.getBreakpoints();

        for (IBreakpoint breakpoint : breakpoints) {
            if (typenames.contains(breakpoint.className())) {
                try {
                    breakpoint.close();
                    breakpoint.install().thenAccept(bp -> {
                        Events.BreakpointEvent bpEvent = new Events.BreakpointEvent("new", this.convertDebuggerBreakpointToClient(bp, context));
                        context.getProtocolServer().sendEvent(bpEvent);
                    });
                } catch (Exception e) {
                    logger.log(Level.SEVERE, String.format("Remove breakpoint exception: %s", e.toString()), e);
                }
            }
        }
    }
}
