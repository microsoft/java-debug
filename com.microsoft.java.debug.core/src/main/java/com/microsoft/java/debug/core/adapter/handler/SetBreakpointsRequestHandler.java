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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.BreakpointManager;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.HotCodeReplaceEvent.EventType;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.SetBreakpointArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.StepEvent;

public class SetBreakpointsRequestHandler implements IDebugRequestHandler {

    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private BreakpointManager manager = new BreakpointManager();

    private boolean registered = false;

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SETBREAKPOINTS);
    }

    @Override
    public void initialize(IDebugAdapterContext context) {
        IDebugRequestHandler.super.initialize(context);
        IHotCodeReplaceProvider provider = context.getProvider(IHotCodeReplaceProvider.class);
        provider.getEventHub()
            .filter(event -> event.getEventType() == EventType.END)
            .subscribe(event -> {
                try {
                    List<String> classNames = (List<String>) event.getData();
                    reinstallBreakpoints(context, classNames);
                } catch (Exception e) {
                    logger.severe(e.toString());
                }
            });
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Empty debug session.");
        }

        if (!registered) {
            registered = true;
            registerBreakpointHandler(context);
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
                } else if (toAdds[i].getHitCount() != added[i].getHitCount() && added[i].className() != null) {
                    // Update hitCount condition.
                    added[i].setHitCount(toAdds[i].getHitCount());
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

    private void registerBreakpointHandler(IDebugAdapterContext context) {
        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            debugSession.getEventHub().events().subscribe(debugEvent -> {
                if (!(debugEvent.event instanceof BreakpointEvent)) {
                    return;
                }
                Event event = debugEvent.event;
                if (debugEvent.eventSet.size() > 1 && debugEvent.eventSet.stream().anyMatch(t -> t instanceof StepEvent)) {
                    // The StepEvent and BreakpointEvent are grouped in the same event set only if they occurs at the same location and in the same thread.
                    // In order to avoid two duplicated StoppedEvents, the debugger will skip the BreakpointEvent.
                } else {
                    ThreadReference bpThread = ((BreakpointEvent) event).thread();
                    IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
                    if (engine.isInEvaluation(bpThread)) {
                        return;
                    }

                    // find the breakpoint related to this breakpoint event
                    IBreakpoint conditionalBP = Arrays.asList(manager.getBreakpoints()).stream().filter(bp -> StringUtils.isNotBlank(bp.getCondition())
                            && bp.requests().contains(((BreakpointEvent) event).request())
                            ).findFirst().get();
                    if (conditionalBP != null) {
                        CompletableFuture.runAsync(() -> {
                            Value value;
                            try {
                                value = engine.evaluate(conditionalBP.getCondition(), bpThread, 0).get();
                                if (value instanceof PrimitiveValue) {
                                    boolean pass = ((PrimitiveValue) value).booleanValue();
                                    if (!pass) {
                                        debugEvent.eventSet.resume();
                                        return;
                                    }
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                // break when the condition evaluation fails
                                e.printStackTrace();
                            }
                            context.getProtocolServer().sendEvent(new Events.StoppedEvent("breakpoint", bpThread.uniqueID()));

                        });
                    } else {
                        context.getProtocolServer().sendEvent(new Events.StoppedEvent("breakpoint", bpThread.uniqueID()));
                    }
                    debugEvent.shouldResume = false;
                }
            });
        }
    }

    private Types.Breakpoint convertDebuggerBreakpointToClient(IBreakpoint breakpoint, IDebugAdapterContext context) {
        int id = (int) breakpoint.getProperty("id");
        boolean verified = breakpoint.getProperty("verified") != null && (boolean) breakpoint.getProperty("verified");
        int lineNumber = AdapterUtils.convertLineNumber(breakpoint.getLineNumber(), context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
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
            breakpoints[i] = context.getDebugSession().createBreakpoint(fqns[i], lines[i], hitCount, sourceBreakpoints[i].condition);
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
