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
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.ContinueArguments;
import com.microsoft.java.debug.core.protocol.Requests.NextArguments;
import com.microsoft.java.debug.core.protocol.Requests.PauseArguments;
import com.microsoft.java.debug.core.protocol.Requests.StepInArguments;
import com.microsoft.java.debug.core.protocol.Requests.StepOutArguments;
import com.microsoft.java.debug.core.protocol.Requests.ThreadsArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.ObjectCollectedException;
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
                logger.severe("stepIn!");
                this.stepIn((StepInArguments) arguments, response, context);
                break;
            case STEPOUT:
                logger.severe("stepOut!");
                this.stepOut((StepOutArguments) arguments, response, context);
                break;
            case NEXT:
                logger.severe("next!");
                this.next((NextArguments) arguments, response, context);
                break;
            case PAUSE:
                logger.severe("pause!");
                this.pause((PauseArguments) arguments, response, context);
                break;
            case CONTINUE:
                logger.severe("resume!");
                this.resume((ContinueArguments) arguments, response, context);
                break;
            default:
                return;
        }
    }

    private void threads(Requests.ThreadsArguments arguments, Response response, IDebugAdapterContext context) {
        ArrayList<Types.Thread> threads = new ArrayList<>();
        try {
            for (ThreadReference thread : context.getDebugSession().getAllThreads()) {
                if (thread.isCollected()) {
                    continue;
                }
                Types.Thread clientThread = new Types.Thread(thread.uniqueID(), "Thread [" + thread.name() + "]");
                threads.add(clientThread);
            }
        } catch (VMDisconnectedException | ObjectCollectedException ex) {
            // allThreads may throw VMDisconnectedException when VM terminates and thread.name() may throw ObjectCollectedException
            // when the thread is exiting.
        }
        response.body = new Responses.ThreadsResponseBody(threads);
    }

    private void stepIn(Requests.StepInArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            DebugUtility.stepInto(thread, context.getDebugSession().getEventHub());
            checkThreadRunningAndRecycleIds(thread, context);
        }
    }

    private void stepOut(Requests.StepOutArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            DebugUtility.stepOut(thread, context.getDebugSession().getEventHub());
            checkThreadRunningAndRecycleIds(thread, context);
        }
    }

    private void next(Requests.NextArguments arguments, Response response, IDebugAdapterContext context) {

        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            DebugUtility.stepOver(thread, context.getDebugSession().getEventHub());
            checkThreadRunningAndRecycleIds(thread, context);
        }
    }

    private void pause(Requests.PauseArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            try {
                thread.suspend();
                context.sendEventAsync(new Events.StoppedEvent("pause", arguments.threadId));
            } catch (VMDisconnectedException ex) {
                AdapterUtils.setErrorResponse(response, ErrorCode.VM_TERMINATED, "Target VM is already terminated.");
            }
        } else {
            context.getDebugSession().suspend();
            context.sendEventAsync(new Events.StoppedEvent("pause", arguments.threadId, true));
        }
    }
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private void resume(Requests.ContinueArguments arguments, Response response, IDebugAdapterContext context) {
        logger.severe("Next managullay" + arguments.threadId);
        boolean allThreadsContinued = true;
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        /**
         * See the jdi doc https://docs.oracle.com/javase/7/docs/jdk/api/jpda/jdi/com/sun/jdi/ThreadReference.html#resume(),
         * suspends of both the virtual machine and individual threads are counted. Before a thread will run again, it must
         * be resumed (through ThreadReference#resume() or VirtualMachine#resume()) the same number of times it has been suspended.
         */
        if (thread != null) {
            allThreadsContinued = false;
            DebugUtility.resumeThread(thread);
            checkThreadRunningAndRecycleIds(thread, context);
        } else {
            context.getDebugSession().resume();
            context.getRecyclableIdPool().removeAllObjects();
        }
        response.body = new Responses.ContinueResponseBody(allThreadsContinued);
    }

    private void checkThreadRunningAndRecycleIds(ThreadReference thread, IDebugAdapterContext context) {
        try {
            boolean allThreadsRunning = !DebugUtility.getAllThreadsSafely(context.getDebugSession()).stream()
                    .anyMatch(ThreadReference::isSuspended);
            if (allThreadsRunning) {
                context.getRecyclableIdPool().removeAllObjects();
            } else {
                context.getRecyclableIdPool().removeObjectsByOwner(thread.uniqueID());
            }
            IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
            engine.clearAll();

        } catch (VMDisconnectedException ex) {
            // isSuspended may throw VMDisconnectedException when the VM terminates
            context.getRecyclableIdPool().removeAllObjects();
        } catch (ObjectCollectedException collectedEx) {
            // isSuspended may throw ObjectCollectedException when the thread terminates
            context.getRecyclableIdPool().removeObjectsByOwner(thread.uniqueID());
        }
    }

}
