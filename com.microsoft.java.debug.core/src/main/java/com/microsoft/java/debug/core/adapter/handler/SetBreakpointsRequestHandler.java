/*******************************************************************************
* Copyright (c) 2017-2022 Microsoft Corporation and others.
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
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.DebugSettings.Switch;
import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.IEvaluatableBreakpoint;
import com.microsoft.java.debug.core.JavaBreakpointLocation;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
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
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequest;

public class SetBreakpointsRequestHandler implements IDebugRequestHandler {

    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

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
        String sourcePath = normalizeSourcePath(bpArguments.source, context);
        // When breakpoint source path is null or an invalid file path, send an ErrorResponse back.
        if (StringUtils.isBlank(sourcePath)) {
            throw AdapterUtils.createCompletionException(
                String.format("Failed to setBreakpoint. Reason: '%s' is an invalid path.", bpArguments.source.path),
                ErrorCode.SET_BREAKPOINT_FAILURE);
        }

        try {
            List<Types.Breakpoint> res = new ArrayList<>();
            IBreakpoint[] toAdds = this.convertClientBreakpointsToDebugger(sourcePath, bpArguments.breakpoints, context);
            // See the VSCode bug https://github.com/Microsoft/vscode/issues/36471.
            // The source uri sometimes is encoded by VSCode, the debugger will decode it to keep the uri consistent.
            IBreakpoint[] added = context.getBreakpointManager()
                                         .setBreakpoints(AdapterUtils.decodeURIComponent(sourcePath), toAdds, bpArguments.sourceModified);
            for (int i = 0; i < bpArguments.breakpoints.length; i++) {
                added[i].setAsync(context.asyncJDWP());
                // For newly added breakpoint, should install it to debuggee first.
                if (toAdds[i] == added[i] && added[i].className() != null) {
                    added[i].install().thenAccept(bp -> {
                        Events.BreakpointEvent bpEvent = new Events.BreakpointEvent("changed", this.convertDebuggerBreakpointToClient(bp, context));
                        context.getProtocolServer().sendEvent(bpEvent);
                    });
                } else if (added[i].className() != null) {
                    if (toAdds[i].getHitCount() != added[i].getHitCount()) {
                        // Update hitCount condition.
                        added[i].setHitCount(toAdds[i].getHitCount());
                    }

                    if (!StringUtils.equals(toAdds[i].getLogMessage(), added[i].getLogMessage())) {
                        added[i].setLogMessage(toAdds[i].getLogMessage());
                    }

                    if (!StringUtils.equals(toAdds[i].getCondition(), added[i].getCondition())) {
                        added[i].setCondition(toAdds[i].getCondition());
                    }

                }
                res.add(this.convertDebuggerBreakpointToClient(added[i], context));
            }
            response.body = new Responses.SetBreakpointsResponseBody(res);
            return CompletableFuture.completedFuture(response);
        } catch (DebugException e) {
            throw AdapterUtils.createCompletionException(
                String.format("Failed to setBreakpoint. Reason: '%s'", e.toString()),
                ErrorCode.SET_BREAKPOINT_FAILURE);
        }
    }

    public static String normalizeSourcePath(Types.Source source, IDebugAdapterContext context) {
        String clientPath = source.path;
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
        if (source.sourceReference != 0 && context.getSourceUri(source.sourceReference) != null) {
            sourcePath = context.getSourceUri(source.sourceReference);
        } else if (StringUtils.isNotBlank(clientPath)) {
            // See the bug https://github.com/Microsoft/vscode/issues/30996
            // Source.path in the SetBreakpointArguments could be a file system path or uri.
            sourcePath = AdapterUtils.convertPath(clientPath, AdapterUtils.isUri(clientPath), context.isDebuggerPathsAreUri());
        }

        return sourcePath;
    }

    private IBreakpoint getAssociatedEvaluatableBreakpoint(IDebugAdapterContext context, BreakpointEvent event) {
        return Arrays.asList(context.getBreakpointManager().getBreakpoints()).stream().filter(
            bp -> {
                return bp instanceof IEvaluatableBreakpoint
                    && ((IEvaluatableBreakpoint) bp).containsEvaluatableExpression()
                    && bp.requests().contains(event.request());
            }
        ).findFirst().orElse(null);
    }

    private void registerBreakpointHandler(IDebugAdapterContext context) {
        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            debugSession.getEventHub().events().filter(debugEvent -> debugEvent.event instanceof BreakpointEvent).subscribe(debugEvent -> {
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
                    IBreakpoint expressionBP = getAssociatedEvaluatableBreakpoint(context, (BreakpointEvent) event);
                    String breakpointName = computeBreakpointName(event.request());

                    if (expressionBP != null) {
                        CompletableFuture.runAsync(() -> {
                            engine.evaluateForBreakpoint((IEvaluatableBreakpoint) expressionBP, bpThread).whenComplete((value, ex) -> {
                                boolean resume = handleEvaluationResult(context, bpThread, (IEvaluatableBreakpoint) expressionBP, value, ex);
                                // Clear the evaluation environment caused by above evaluation.
                                engine.clearState(bpThread);

                                if (resume) {
                                    debugEvent.eventSet.resume();
                                } else {
                                    context.getThreadCache().addEventThread(bpThread, breakpointName);
                                    boolean allThreadsStopped = event.request() != null
                                            && event.request().suspendPolicy() == EventRequest.SUSPEND_ALL;
                                    context.getProtocolServer().sendEvent(new Events.StoppedEvent(
                                            breakpointName, bpThread.uniqueID(), allThreadsStopped));
                                }
                            });
                        });
                    } else {
                        context.getThreadCache().addEventThread(bpThread, breakpointName);
                        boolean allThreadsStopped = event.request() != null
                                && event.request().suspendPolicy() == EventRequest.SUSPEND_ALL;
                        context.getProtocolServer().sendEvent(new Events.StoppedEvent(
                                breakpointName, bpThread.uniqueID(), allThreadsStopped));
                    }
                    debugEvent.shouldResume = false;
                }
            });
        }
    }

    private String computeBreakpointName(EventRequest request) {
        switch ((int) request.getProperty(IBreakpoint.REQUEST_TYPE)) {
            case IBreakpoint.REQUEST_TYPE_LAMBDA:
                return "lambda breakpoint";
            case IBreakpoint.REQUEST_TYPE_METHOD:
                return "function breakpoint";
            default:
                return "breakpoint";
        }
    }

    /**
     * Check whether the condition expression is satisfied, and return a boolean value to determine to resume the thread or not.
     */
    public static boolean handleEvaluationResult(IDebugAdapterContext context, ThreadReference bpThread, IEvaluatableBreakpoint breakpoint,
        Value value, Throwable ex) {
        if (StringUtils.isNotBlank(breakpoint.getLogMessage())) {
            if (ex != null) {
                logger.log(Level.SEVERE, String.format("[Logpoint]: %s", ex.getMessage() != null ? ex.getMessage() : ex.toString()), ex);
                context.getProtocolServer().sendEvent(new Events.UserNotificationEvent(
                    Events.UserNotificationEvent.NotificationType.ERROR,
                    String.format("[Logpoint] Log message '%s' error: %s", breakpoint.getLogMessage(), ex.getMessage())));
            } else if (value != null) {
                if (value instanceof StringReference) {
                    String message = ((StringReference) value).value();
                    context.getProtocolServer().sendEvent(Events.OutputEvent.createConsoleOutput(
                        message + System.lineSeparator()));
                }
            }
            return true;
        } else {
            boolean resume = false;
            boolean resultNotBoolean = false;
            if (value != null && ex == null) {
                if (value instanceof BooleanValue) {
                    resume = !((BooleanValue) value).booleanValue();
                } else if (value instanceof ObjectReference
                        && ((ObjectReference) value).type().name().equals("java.lang.Boolean")) {
                    // get boolean value from java.lang.Boolean object
                    Field field = ((ReferenceType) ((ObjectReference) value).type()).fieldByName("value");
                    resume = !((BooleanValue) ((ObjectReference) value).getValue(field)).booleanValue();
                } else {
                    resultNotBoolean = true;
                }
            }
            if (resume) {
                return true;
            } else {
                if (context.isVmTerminated()) {
                    // do nothing
                } else if (ex != null) {
                    if (!(ex instanceof VMDisconnectedException || ex.getCause() instanceof VMDisconnectedException)) {
                        logger.log(Level.SEVERE, String.format("[ConditionalBreakpoint]: %s", ex.getMessage() != null ? ex.getMessage() : ex.toString()), ex);
                        context.getProtocolServer().sendEvent(new Events.UserNotificationEvent(
                                Events.UserNotificationEvent.NotificationType.ERROR,
                                String.format("Breakpoint condition '%s' error: %s", breakpoint.getCondition(), ex.getMessage())));
                    }
                } else if (value == null || resultNotBoolean) {
                    context.getProtocolServer().sendEvent(new Events.UserNotificationEvent(
                            Events.UserNotificationEvent.NotificationType.WARNING,
                            String.format("Result of breakpoint condition '%s' is not a boolean, please correct your expression.", breakpoint.getCondition())));
                }
                return false;
            }
        }
    }

    private Types.Breakpoint convertDebuggerBreakpointToClient(IBreakpoint breakpoint, IDebugAdapterContext context) {
        int id = (int) breakpoint.getProperty("id");
        boolean verified = breakpoint.getProperty("verified") != null && (boolean) breakpoint.getProperty("verified");
        int lineNumber = AdapterUtils.convertLineNumber(breakpoint.sourceLocation().lineNumberInSourceFile(),
                                            context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
        return new Types.Breakpoint(id, verified, lineNumber, "");
    }

    private IBreakpoint[] convertClientBreakpointsToDebugger(String sourceFile, Types.SourceBreakpoint[] sourceBreakpoints, IDebugAdapterContext context)
            throws DebugException {
        Types.SourceBreakpoint[] debugSourceBreakpoints = Stream.of(sourceBreakpoints).map(sourceBreakpoint -> {
            int line = AdapterUtils.convertLineNumber(sourceBreakpoint.line, context.isClientLinesStartAt1(), context.isDebuggerLinesStartAt1());
            int column = AdapterUtils.convertColumnNumber(sourceBreakpoint.column, context.isClientColumnsStartAt1(), context.isDebuggerColumnsStartAt1());
            return new Types.SourceBreakpoint(line, column);
        }).toArray(Types.SourceBreakpoint[]::new);

        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        JavaBreakpointLocation[] locations = sourceProvider.getBreakpointLocations(sourceFile, debugSourceBreakpoints);
        IBreakpoint[] breakpoints = new IBreakpoint[locations.length];
        for (int i = 0; i < locations.length; i++) {
            int hitCount = 0;
            try {
                hitCount = Integer.parseInt(sourceBreakpoints[i].hitCondition);
            } catch (NumberFormatException e) {
                hitCount = 0; // If hitCount is an illegal number, ignore hitCount condition.
            }

            if (DebugSettings.getCurrent().debugSupportOnDecompiledSource == Switch.ON) {
                // Align the decompiled line with the original line.
                int[] lineMappings = sourceProvider.getDecompiledLineMappings(sourceFile);
                if (locations[i] != null && lineMappings != null) {
                    int lineNumberInSourceFile = locations[i].lineNumber();
                    int[] originalLines = AdapterUtils.binarySearchMappedLines(lineMappings, lineNumberInSourceFile);
                    if (originalLines != null && originalLines.length > 0) {
                        locations[i].setLineNumberInSourceFile(lineNumberInSourceFile);
                        locations[i].setLineNumber(originalLines[0]);
                    }
                }
            }
            breakpoints[i] = context.getDebugSession().createBreakpoint(locations[i], hitCount, sourceBreakpoints[i].condition,
                sourceBreakpoints[i].logMessage);
            if (sourceProvider.supportsRealtimeBreakpointVerification() && StringUtils.isNotBlank(locations[i].className())) {
                breakpoints[i].putProperty("verified", true);
            }
        }
        return breakpoints;
    }

    private void reinstallBreakpoints(IDebugAdapterContext context, List<String> typenames) {
        if (typenames == null || typenames.isEmpty()) {
            return;
        }
        IBreakpoint[] breakpoints = context.getBreakpointManager().getBreakpoints();

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
