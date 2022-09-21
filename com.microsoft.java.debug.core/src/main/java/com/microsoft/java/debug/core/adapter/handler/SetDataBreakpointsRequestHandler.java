/*******************************************************************************
* Copyright (c) 2019-2022 Microsoft Corporation and others.
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.IEvaluatableBreakpoint;
import com.microsoft.java.debug.core.IWatchpoint;
import com.microsoft.java.debug.core.Watchpoint;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Events.BreakpointEvent;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.SetDataBreakpointsArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types.Breakpoint;
import com.microsoft.java.debug.core.protocol.Types.DataBreakpoint;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.WatchpointEvent;

public class SetDataBreakpointsRequestHandler implements IDebugRequestHandler {
    private boolean registered = false;

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SETDATABREAKPOINTS);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Empty debug session.");
        }

        if (!registered) {
            registered = true;
            registerWatchpointHandler(context);
        }

        SetDataBreakpointsArguments dataBpArgs = (SetDataBreakpointsArguments) arguments;
        IWatchpoint[] requestedWatchpoints = (dataBpArgs.breakpoints == null) ? new Watchpoint[0] : new Watchpoint[dataBpArgs.breakpoints.length];
        for (int i = 0; i < requestedWatchpoints.length; i++) {
            DataBreakpoint dataBreakpoint = dataBpArgs.breakpoints[i];
            if (dataBreakpoint.dataId != null) {
                String[] segments = dataBreakpoint.dataId.split("#");
                if (segments.length == 2 && StringUtils.isNotBlank(segments[0]) && StringUtils.isNotBlank(segments[1])) {
                    int hitCount = 0;
                    try {
                        hitCount = Integer.parseInt(dataBreakpoint.hitCondition);
                    } catch (NumberFormatException e) {
                        hitCount = 0; // If hitCount is an illegal number, ignore hitCount condition.
                    }

                    String accessType = dataBreakpoint.accessType != null ? dataBreakpoint.accessType.label() : null;
                    requestedWatchpoints[i] = context.getDebugSession().createWatchPoint(segments[0], segments[1], accessType,
                                                dataBreakpoint.condition, hitCount);
                }
            }
        }

        IWatchpoint[] currentWatchpoints = context.getBreakpointManager().setWatchpoints(requestedWatchpoints);
        List<Breakpoint> breakpoints = new ArrayList<>();
        for (int i = 0; i < currentWatchpoints.length; i++) {
            if (currentWatchpoints[i] == null) {
                breakpoints.add(new Breakpoint(false));
                continue;
            }

            // If the requested watchpoint exists in the watchpoint manager, it will reuse the cached watchpoint object.
            // Otherwise add the requested watchpoint to the cache.
            // So if the returned watchpoint from the manager is same as the requested wantchpoint, this means it's a new watchpoint, need install it.
            if (currentWatchpoints[i] == requestedWatchpoints[i]) {
                currentWatchpoints[i].install().thenAccept(wp -> {
                    BreakpointEvent bpEvent = new BreakpointEvent("new", convertDebuggerWatchpointToClient(wp));
                    context.getProtocolServer().sendEvent(bpEvent);
                });
            } else {
                if (currentWatchpoints[i].getHitCount() != requestedWatchpoints[i].getHitCount()) {
                    currentWatchpoints[i].setHitCount(requestedWatchpoints[i].getHitCount());
                }

                if (!Objects.equals(currentWatchpoints[i].getCondition(), requestedWatchpoints[i].getCondition())) {
                    currentWatchpoints[i].setCondition(requestedWatchpoints[i].getCondition());
                }
            }

            breakpoints.add(convertDebuggerWatchpointToClient(currentWatchpoints[i]));
        }

        response.body = new Responses.SetDataBreakpointsResponseBody(breakpoints);
        return CompletableFuture.completedFuture(response);
    }

    private Breakpoint convertDebuggerWatchpointToClient(IWatchpoint watchpoint) {
        return new Breakpoint((int) watchpoint.getProperty("id"),
            watchpoint.getProperty("verified") != null && (boolean) watchpoint.getProperty("verified"));
    }

    private void registerWatchpointHandler(IDebugAdapterContext context) {
        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            debugSession.getEventHub().events().filter(debugEvent -> debugEvent.event instanceof WatchpointEvent).subscribe(debugEvent -> {
                Event event = debugEvent.event;
                ThreadReference bpThread = ((WatchpointEvent) event).thread();
                IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
                if (engine.isInEvaluation(bpThread)) {
                    return;
                }

                // Find the watchpoint related to this watchpoint event
                IWatchpoint watchpoint = Stream.of(context.getBreakpointManager().getWatchpoints())
                    .filter(wp -> {
                        return wp instanceof IEvaluatableBreakpoint
                            && ((IEvaluatableBreakpoint) wp).containsEvaluatableExpression()
                            && wp.requests().contains(event.request());
                    })
                    .findFirst().orElse(null);

                if (watchpoint != null) {
                    CompletableFuture.runAsync(() -> {
                        engine.evaluateForBreakpoint((IEvaluatableBreakpoint) watchpoint, bpThread).whenComplete((value, ex) -> {
                            boolean resume = SetBreakpointsRequestHandler.handleEvaluationResult(
                                                context, bpThread, (IEvaluatableBreakpoint) watchpoint, value, ex);
                            // Clear the evaluation environment caused by above evaluation.
                            engine.clearState(bpThread);

                            if (resume) {
                                debugEvent.eventSet.resume();
                            } else {
                                context.getThreadCache().addEventThread(bpThread);
                                context.getProtocolServer().sendEvent(new Events.StoppedEvent("data breakpoint", bpThread.uniqueID()));
                            }
                        });
                    });
                } else {
                    context.getThreadCache().addEventThread(bpThread);
                    context.getProtocolServer().sendEvent(new Events.StoppedEvent("data breakpoint", bpThread.uniqueID()));
                }
                debugEvent.shouldResume = false;
            });
        }
    }
}
