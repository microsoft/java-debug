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

import org.eclipse.jdt.ls.debug.DebugEvent;
import org.eclipse.jdt.ls.debug.IDebugSession;
import org.eclipse.jdt.ls.debug.adapter.AdapterUtils;
import org.eclipse.jdt.ls.debug.adapter.ErrorCode;
import org.eclipse.jdt.ls.debug.adapter.Events;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;

public class ConfigurationDoneRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.CONFIGURATIONDONE);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            // This is a global event handler to handle the JDI Event from Virtual Machine.
            debugSession.eventHub().events().subscribe(debugEvent -> {
                handleDebugEvent(debugEvent, debugSession, context);
            });
            // configuration is done, and start debug session.
            debugSession.start();
        } else {
            context.sendEventAsync(new Events.TerminatedEvent());
            AdapterUtils.setErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Failed to launch debug session, the debugger will exit.");
        }
    }

    private void handleDebugEvent(DebugEvent debugEvent, IDebugSession debugSession, IDebugAdapterContext context) {
        Event event = debugEvent.event;
        if (event instanceof VMStartEvent) {
            // do nothing.
        } else if (event instanceof VMDeathEvent) {
            context.sendEventAsync(new Events.ExitedEvent(0));
        } else if (event instanceof VMDisconnectEvent) {
            context.sendEventAsync(new Events.TerminatedEvent());
            // Terminate eventHub thread.
            try {
                debugSession.eventHub().close();
            } catch (Exception e) {
                // do nothing.
            }
        } else if (event instanceof ThreadStartEvent) {
            ThreadReference startThread = ((ThreadStartEvent) event).thread();
            Events.ThreadEvent threadEvent = new Events.ThreadEvent("started", startThread.uniqueID());
            context.sendEventAsync(threadEvent);
        } else if (event instanceof ThreadDeathEvent) {
            ThreadReference deathThread = ((ThreadDeathEvent) event).thread();
            Events.ThreadEvent threadDeathEvent = new Events.ThreadEvent("exited", deathThread.uniqueID());
            context.sendEventAsync(threadDeathEvent);
        } else if (event instanceof BreakpointEvent) {
            ThreadReference bpThread = ((BreakpointEvent) event).thread();
            context.sendEventAsync(new Events.StoppedEvent("breakpoint", bpThread.uniqueID()));
            debugEvent.shouldResume = false;
        } else if (event instanceof StepEvent) {
            ThreadReference stepThread = ((StepEvent) event).thread();
            context.sendEventAsync(new Events.StoppedEvent("step", stepThread.uniqueID()));
            debugEvent.shouldResume = false;
        } else if (event instanceof ExceptionEvent) {
            ThreadReference thread = ((ExceptionEvent) event).thread();
            context.sendEventAsync(new Events.StoppedEvent("exception", thread.uniqueID()));
            debugEvent.shouldResume = false;
        }
    }
}
