/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Gayan Perera - initial API and implementation
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
import com.microsoft.java.debug.core.IMethodBreakpoint;
import com.microsoft.java.debug.core.MethodBreakpoint;
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
import com.microsoft.java.debug.core.protocol.Requests.SetFunctionBreakpointsArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types.Breakpoint;
import com.microsoft.java.debug.core.protocol.Types.FunctionBreakpoint;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.MethodEntryEvent;

public class SetFunctionBreakpointsRequestHandler implements IDebugRequestHandler {
    private boolean registered = false;

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SETFUNCTIONBREAKPOINTS);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION,
                    "Empty debug session.");
        }

        if (!registered) {
            registered = true;
            registerMethodBreakpointHandler(context);
        }

        SetFunctionBreakpointsArguments funcBpArgs = (SetFunctionBreakpointsArguments) arguments;
        IMethodBreakpoint[] requestedMethodBreakpoints = (funcBpArgs.breakpoints == null) ? new IMethodBreakpoint[0]
                : new MethodBreakpoint[funcBpArgs.breakpoints.length];
        for (int i = 0; i < requestedMethodBreakpoints.length; i++) {
            FunctionBreakpoint funcBreakpoint = funcBpArgs.breakpoints[i];
            if (funcBreakpoint.name != null) {
                String[] segments = funcBreakpoint.name.split("#");
                if (segments.length == 2 && StringUtils.isNotBlank(segments[0])
                        && StringUtils.isNotBlank(segments[1])) {
                    int hitCount = 0;
                    try {
                        hitCount = Integer.parseInt(funcBreakpoint.hitCondition);
                    } catch (NumberFormatException e) {
                        hitCount = 0; // If hitCount is an illegal number, ignore hitCount condition.
                    }
                    requestedMethodBreakpoints[i] = context.getDebugSession().createFunctionBreakpoint(segments[0],
                            segments[1],
                            funcBreakpoint.condition, hitCount);
                }
            }
        }

        IMethodBreakpoint[] currentMethodBreakpoints = context.getBreakpointManager()
                .setMethodBreakpoints(requestedMethodBreakpoints);
        List<Breakpoint> breakpoints = new ArrayList<>();
        for (int i = 0; i < currentMethodBreakpoints.length; i++) {
            if (currentMethodBreakpoints[i] == null) {
                breakpoints.add(new Breakpoint(false));
                continue;
            }

            // If the requested method breakpoint exists in the manager, it will reuse
            // the cached breakpoint exists object.
            // Otherwise add the requested method breakpoint to the cache.
            // So if the returned method breakpoint from the manager is same as the
            // requested method breakpoint, this means it's a new method breakpoint, need
            // install it.
            if (currentMethodBreakpoints[i] == requestedMethodBreakpoints[i]) {
                currentMethodBreakpoints[i].install().thenAccept(wp -> {
                    BreakpointEvent bpEvent = new BreakpointEvent("new", convertDebuggerMethodToClient(wp));
                    context.getProtocolServer().sendEvent(bpEvent);
                });
            } else {
                if (currentMethodBreakpoints[i].getHitCount() != requestedMethodBreakpoints[i].getHitCount()) {
                    currentMethodBreakpoints[i].setHitCount(requestedMethodBreakpoints[i].getHitCount());
                }

                if (!Objects.equals(currentMethodBreakpoints[i].getCondition(),
                        requestedMethodBreakpoints[i].getCondition())) {
                    currentMethodBreakpoints[i].setCondition(requestedMethodBreakpoints[i].getCondition());
                }
            }

            breakpoints.add(convertDebuggerMethodToClient(currentMethodBreakpoints[i]));
        }

        response.body = new Responses.SetDataBreakpointsResponseBody(breakpoints);
        return CompletableFuture.completedFuture(response);
    }

    private Breakpoint convertDebuggerMethodToClient(IMethodBreakpoint methodBreakpoint) {
        return new Breakpoint((int) methodBreakpoint.getProperty("id"),
                methodBreakpoint.getProperty("verified") != null && (boolean) methodBreakpoint.getProperty("verified"));
    }

    private void registerMethodBreakpointHandler(IDebugAdapterContext context) {
        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            debugSession.getEventHub().events().filter(debugEvent -> debugEvent.event instanceof MethodEntryEvent)
                    .subscribe(debugEvent -> {
                        MethodEntryEvent methodEntryEvent = (MethodEntryEvent) debugEvent.event;
                        ThreadReference bpThread = methodEntryEvent.thread();
                        IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);

                        // Find the method breakpoint related to this method entry event
                        IMethodBreakpoint methodBreakpoint = Stream
                                .of(context.getBreakpointManager().getMethodBreakpoints())
                                .filter(mp -> {
                                    return mp.requests().contains(methodEntryEvent.request())
                                            && matches(methodEntryEvent, mp);
                                })
                                .findFirst().orElse(null);

                        if (methodBreakpoint != null) {
                            if (methodBreakpoint instanceof IEvaluatableBreakpoint
                                    && ((IEvaluatableBreakpoint) methodBreakpoint).containsConditionalExpression()) {
                                if (engine.isInEvaluation(bpThread)) {
                                    return;
                                }
                                CompletableFuture.runAsync(() -> {
                                    engine.evaluateForBreakpoint((IEvaluatableBreakpoint) methodBreakpoint, bpThread)
                                            .whenComplete((value, ex) -> {
                                                boolean resume = SetBreakpointsRequestHandler.handleEvaluationResult(
                                                        context, bpThread, (IEvaluatableBreakpoint) methodBreakpoint,
                                                        value,
                                                        ex);
                                                // Clear the evaluation environment caused by above evaluation.
                                                engine.clearState(bpThread);

                                                if (resume) {
                                                    debugEvent.eventSet.resume();
                                                } else {
                                                    context.getProtocolServer().sendEvent(new Events.StoppedEvent(
                                                            "function breakpoint", bpThread.uniqueID()));
                                                }
                                            });
                                });

                            } else {
                                context.getProtocolServer()
                                        .sendEvent(new Events.StoppedEvent("function breakpoint", bpThread.uniqueID()));
                            }

                        } else {
                            debugEvent.eventSet.resume();
                        }
                        debugEvent.shouldResume = false;
                    });
        }
    }

    private boolean matches(MethodEntryEvent methodEntryEvent, IMethodBreakpoint breakpoint) {
        return breakpoint.className().equals(methodEntryEvent.location().declaringType().name())
                && breakpoint.methodName().equals(methodEntryEvent.method().name());
    }

}
