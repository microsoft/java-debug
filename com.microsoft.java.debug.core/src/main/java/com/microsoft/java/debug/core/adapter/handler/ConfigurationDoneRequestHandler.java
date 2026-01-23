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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugEvent;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.JdiExceptionReference;
import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.event.VMStartEvent;

public class ConfigurationDoneRequestHandler implements IDebugRequestHandler {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private VMHandler vmHandler = new VMHandler();

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.CONFIGURATIONDONE);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        IDebugSession debugSession = context.getDebugSession();
        vmHandler.setVmProvider(context.getProvider(IVirtualMachineManagerProvider.class));
        if (debugSession != null) {
            // This is a global event handler to handle the JDI Event from Virtual Machine.
            debugSession.getEventHub().events().subscribe(debugEvent -> {
                handleDebugEvent(debugEvent, debugSession, context);
            });
            // configuration is done, and start debug session.
            debugSession.start();
            return CompletableFuture.completedFuture(response);
        } else {
            context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Failed to launch debug session, the debugger will exit.");
        }
    }

    private void handleDebugEvent(DebugEvent debugEvent, IDebugSession debugSession, IDebugAdapterContext context) {
        Event event = debugEvent.event;
        boolean isImportantEvent = true;
        if (event instanceof VMStartEvent) {
            if (context.isVmStopOnEntry()) {
                DebugUtility.stopOnEntry(debugSession, context.getMainClass()).thenAccept(threadId -> {
                    context.getProtocolServer().sendEvent(new Events.StoppedEvent("entry", threadId));
                    context.getThreadCache().setThreadStoppedReason(threadId, "entry");
                });
            }
        } else if (event instanceof VMDeathEvent) {
            vmHandler.disconnectVirtualMachine(event.virtualMachine());
            context.setVmTerminated();
            context.getProtocolServer().sendEvent(new Events.ExitedEvent(0));
        } else if (event instanceof VMDisconnectEvent) {
            vmHandler.disconnectVirtualMachine(event.virtualMachine());
            if (context.isAttached()) {
                context.setVmTerminated();
                context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
                // Terminate eventHub thread.
                try {
                    debugSession.getEventHub().close();
                } catch (Exception e) {
                    // do nothing.
                }
            } else {
                // Skip it when the debugger is in launch mode, because LaunchRequestHandler will handle the event there.
            }
        } else if (event instanceof ThreadStartEvent) {
            ThreadReference startThread = ((ThreadStartEvent) event).thread();
            Events.ThreadEvent threadEvent = new Events.ThreadEvent("started", startThread.uniqueID());
            context.getProtocolServer().sendEvent(threadEvent);
        } else if (event instanceof ThreadDeathEvent) {
            ThreadReference deathThread = ((ThreadDeathEvent) event).thread();
            Events.ThreadEvent threadDeathEvent = new Events.ThreadEvent("exited", deathThread.uniqueID());
            context.getProtocolServer().sendEvent(threadDeathEvent);
            context.getThreadCache().addDeathThread(deathThread.uniqueID());
        } else if (event instanceof BreakpointEvent) {
            // ignore since SetBreakpointsRequestHandler has already handled
        } else if (event instanceof ExceptionEvent) {
            ThreadReference thread = ((ExceptionEvent) event).thread();
            IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
            if (engine.isInEvaluation(thread)) {
                return;
            }

            JdiExceptionReference jdiException = new JdiExceptionReference(((ExceptionEvent) event).exception(),
                    ((ExceptionEvent) event).catchLocation() == null);
            context.getExceptionManager().setException(thread.uniqueID(), jdiException);
            context.getThreadCache().addEventThread(thread, "exception");
            boolean allThreadsStopped = event.request() != null
                    && event.request().suspendPolicy() == EventRequest.SUSPEND_ALL;
            context.getProtocolServer().sendEvent(new Events.StoppedEvent("exception", thread.uniqueID(), allThreadsStopped));
            debugEvent.shouldResume = false;
        } else {
            isImportantEvent = false;
        }

        // record events of important types only, to get rid of noises.
        if (isImportantEvent) {
            UsageDataSession.recordEvent(event);
        }
    }
}
