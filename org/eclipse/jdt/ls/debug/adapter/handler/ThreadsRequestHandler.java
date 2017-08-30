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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.ls.debug.DebugUtility;
import org.eclipse.jdt.ls.debug.adapter.AdapterUtils;
import org.eclipse.jdt.ls.debug.adapter.ErrorCode;
import org.eclipse.jdt.ls.debug.adapter.Events;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.Requests.ContinueArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.NextArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.PauseArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.StepInArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.StepOutArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.ThreadsArguments;
import org.eclipse.jdt.ls.debug.adapter.Responses;
import org.eclipse.jdt.ls.debug.adapter.Types;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;

public class ThreadsRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.THREADS, Command.STEPIN, Command.STEPOUT, Command.NEXT, Command.PAUSE, Command.CONTINUE);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            AdapterUtils.setErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Debug Session doesn't exist.");
            return;
        }
        switch (command) {
            case THREADS:
                this.threads((ThreadsArguments) arguments, response, context);
                break;
            case STEPIN:
                this.stepIn((StepInArguments) arguments, response, context);
                break;
            case STEPOUT:
                this.stepOut((StepOutArguments) arguments, response, context);
                break;
            case NEXT:
                this.next((NextArguments) arguments, response, context);
                break;
            case PAUSE:
                this.pause((PauseArguments) arguments, response, context);
                break;
            case CONTINUE:
                this.resume((ContinueArguments) arguments, response, context);
                break;
            default:
                return;
        }
    }

    private void threads(Requests.ThreadsArguments arguments, Response response, IDebugAdapterContext context) {
        ArrayList<Types.Thread> threads = new ArrayList<>();
        for (ThreadReference thread : DebugUtility.getAllThreadsSafely(context.getDebugSession())) {
            Types.Thread clientThread = new Types.Thread(thread.uniqueID(), "Thread [" + thread.name() + "]");
            threads.add(clientThread);
        }
        response.body = new Responses.ThreadsResponseBody(threads);
    }

    private void stepIn(Requests.StepInArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            DebugUtility.stepInto(thread, context.getDebugSession().eventHub());
            checkThreadRunningAndRecycleIds(thread, context);
        }
    }

    private void stepOut(Requests.StepOutArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            DebugUtility.stepOut(thread, context.getDebugSession().eventHub());
            checkThreadRunningAndRecycleIds(thread, context);
        }
    }

    private void next(Requests.NextArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            DebugUtility.stepOver(thread, context.getDebugSession().eventHub());
            checkThreadRunningAndRecycleIds(thread, context);
        }
    }

    private void pause(Requests.PauseArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            thread.suspend();
            context.sendEventAsync(new Events.StoppedEvent("pause", arguments.threadId));
        } else {
            context.getDebugSession().suspend();
            context.sendEventAsync(new Events.StoppedEvent("pause", arguments.threadId, true));
        }
    }

    private void resume(Requests.ContinueArguments arguments, Response response, IDebugAdapterContext context) {
        boolean allThreadsContinued = true;
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            allThreadsContinued = false;
            thread.resume();
            checkThreadRunningAndRecycleIds(thread, context);
        } else {
            context.getDebugSession().resume();
            context.getRecyclableIdPool().removeAllObjects();
        }
        response.body = new Responses.ContinueResponseBody(allThreadsContinued);
    }

    private void checkThreadRunningAndRecycleIds(ThreadReference thread, IDebugAdapterContext context) {
        try {
            boolean allThreadsRunning = !DebugUtility.getAllThreadsSafely(context.getDebugSession())
                    .stream().anyMatch(ThreadReference::isSuspended);
            if (allThreadsRunning) {
                context.getRecyclableIdPool().removeAllObjects();
            } else {
                context.getRecyclableIdPool().removeObjectsByOwner(thread.uniqueID());
            }
        } catch (VMDisconnectedException ex) {
            context.getRecyclableIdPool().removeAllObjects();
        }
    }

}
