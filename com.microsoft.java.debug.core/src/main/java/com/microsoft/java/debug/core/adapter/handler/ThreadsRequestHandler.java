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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.java.debug.core.DebugEvent;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.JDIMethod;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
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
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.StepRequest;

import io.reactivex.disposables.Disposable;

public class ThreadsRequestHandler implements IDebugRequestHandler {
    private Disposable eventHandler = null;
    private Map<Long, ThreadState> threadStates = new HashMap<>();

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

        if (eventHandler == null) {
            eventHandler = context.getDebugSession().getEventHub().stepEvents().subscribe(debugEvent -> {
                handleDebugEvent(debugEvent, context.getDebugSession(), context);
            });
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
            setPendingStepKind(thread, StepRequest.STEP_INTO);
            setStackDepth(thread);
            setStepLocation(thread);
            DebugUtility.stepInto(thread, context.getDebugSession().getEventHub(), context.getStepFilters());
            checkThreadRunningAndRecycleIds(thread, context);
        }
    }

    private void stepOut(Requests.StepOutArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            setPendingStepKind(thread, StepRequest.STEP_OUT);
            DebugUtility.stepOut(thread, context.getDebugSession().getEventHub(), context.getStepFilters());
            checkThreadRunningAndRecycleIds(thread, context);
        }
    }

    private void next(Requests.NextArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            setPendingStepKind(thread, StepRequest.STEP_OVER);
            DebugUtility.stepOver(thread, context.getDebugSession().getEventHub(), context.getStepFilters());
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

    private void resume(Requests.ContinueArguments arguments, Response response, IDebugAdapterContext context) {
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
        } catch (VMDisconnectedException ex) {
            // isSuspended may throw VMDisconnectedException when the VM terminates
            context.getRecyclableIdPool().removeAllObjects();
        } catch (ObjectCollectedException collectedEx) {
            // isSuspended may throw ObjectCollectedException when the thread terminates
            context.getRecyclableIdPool().removeObjectsByOwner(thread.uniqueID());
        }
    }

    private void handleDebugEvent(DebugEvent debugEvent, IDebugSession debugSession, IDebugAdapterContext context) {
        StepEvent event = (StepEvent) debugEvent.event;
        ThreadReference thread = event.thread();
        debugEvent.shouldResume = false;
        if (context.isJustMyCode()) {
            if (getPendingStepKind(thread) == StepRequest.STEP_INTO) {
                if (!context.getStepThroughFilters()) {
                    // Check if the step into operation stepped through the filtered code and stopped at an un-filtered location.
                    if (getStackDepth(thread) + 1 < DebugUtility.getFrameCount(thread)) {
                        // Create another stepOut request to return back where we started the step into.
                        DebugUtility.stepOut(thread, debugSession.getEventHub(), context.getStepFilters());
                        return;
                    }
                }
                // If the ending step location is filtered, or same as the original location where the step into operation is originated,
                // do another step of the same kind.
                if (methodShouldBeFiltered(thread, context) || shouldDoExtraStepInto(thread)) {
                    DebugUtility.stepInto(thread, debugSession.getEventHub(), context.getStepFilters());
                    return;
                }
            }
        }
        context.sendEventAsync(new Events.StoppedEvent("step", thread.uniqueID()));
    }

    /**
     * Return true if the StepEvent's location is a Method that the user has indicated (via the user preferences)
     * should be filtered.
     */
    private boolean methodShouldBeFiltered(ThreadReference thread, IDebugAdapterContext context) {
        Location originalLocation = getStepLocation(thread);
        Location currentLocation = null;
        StackFrame topFrame = DebugUtility.getTopFrame(thread);
        if (topFrame != null) {
            currentLocation = topFrame.location();
        }
        if (originalLocation == null || currentLocation == null) {
            return false;
        }
        return !methodIsFiltered(originalLocation.method(), context) && methodIsFiltered(currentLocation.method(), context);
    }

    private boolean methodIsFiltered(Method method, IDebugAdapterContext context) {
        if (method.isStaticInitializer()
                || method.isSynthetic()
                || method.isConstructor()
                || (context.isSkipSimpleGetters() && JDIMethod.isGetterMethod(method))
                || JDIMethod.isSetterMethod(method)) {
            return true;
        }
        return false;
    }

    /**
     * Check if the current top stack is same as the original top stack.
     */
    private boolean shouldDoExtraStepInto(ThreadReference thread) {
        if (getStackDepth(thread) != DebugUtility.getFrameCount(thread)) {
            return false;
        }
        Location originalLocation = getStepLocation(thread);
        if (originalLocation == null) {
            return false;
        }
        Location currentLocation = DebugUtility.getTopFrame(thread).location();
        Method originalMethod = originalLocation.method();
        Method currentMethod = currentLocation.method();
        if (!originalMethod.equals(currentMethod)) {
            return false;
        }
        if (originalLocation.lineNumber() != currentLocation.lineNumber()) {
            return false;
        }
        return true;
    }

    private ThreadState getThreadState(ThreadReference thread) {
        if (thread  == null) {
            return null;
        }
        long threadId = thread.uniqueID();
        if (!threadStates.containsKey(threadId)) {
            threadStates.put(threadId, new ThreadState());
        }
        return threadStates.get(threadId);
    }

    private void setPendingStepKind(ThreadReference thread, int kind) {
        ThreadState state = getThreadState(thread);
        if (state != null) {
            state.pendingStepKind = kind;
        }
    }

    private int getPendingStepKind(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state == null) {
            return -1;
        }
        return state.pendingStepKind;
    }

    private void setStackDepth(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state != null) {
            state.stackDepth = DebugUtility.getFrameCount(thread);
        }
    }

    private int getStackDepth(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state == null) {
            return -1;
        }
        return state.stackDepth;
    }

    private void setStepLocation(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state != null) {
            StackFrame topFrame = DebugUtility.getTopFrame(thread);
            if (topFrame != null) {
                state.stepLocation = topFrame.location();
            }
        }
    }

    private Location getStepLocation(ThreadReference thread) {
        ThreadState state = getThreadState(thread);
        if (state != null) {
            return state.stepLocation;
        }
        return null;
    }

    class ThreadState {
        int pendingStepKind = -1;
        StepRequest pendingStepRequest = null;
        int stackDepth = -1;
        Location stepLocation = null;
    }
}
