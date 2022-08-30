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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.microsoft.java.debug.core.AsyncJdwpUtils;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
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
import com.microsoft.java.debug.core.protocol.Requests.PauseArguments;
import com.microsoft.java.debug.core.protocol.Requests.ThreadOperationArguments;
import com.microsoft.java.debug.core.protocol.Requests.ThreadsArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;

public class ThreadsRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.THREADS, Command.PAUSE, Command.CONTINUE, Command.CONTINUEALL,
                Command.CONTINUEOTHERS, Command.PAUSEALL, Command.PAUSEOTHERS);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION,
                    "Debug Session doesn't exist.");
        }

        switch (command) {
            case THREADS:
                return this.threads((ThreadsArguments) arguments, response, context);
            case PAUSE:
                return this.pause((PauseArguments) arguments, response, context);
            case CONTINUE:
                return this.resume((ContinueArguments) arguments, response, context);
            case CONTINUEALL:
                return this.resumeAll((ThreadOperationArguments) arguments, response, context);
            case CONTINUEOTHERS:
                return this.resumeOthers((ThreadOperationArguments) arguments, response, context);
            case PAUSEALL:
                return this.pauseAll((ThreadOperationArguments) arguments, response, context);
            case PAUSEOTHERS:
                return this.pauseOthers((ThreadOperationArguments) arguments, response, context);
            default:
                return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE,
                        String.format("Unrecognized request: { _request: %s }", command.toString()));
        }
    }

    private CompletableFuture<Response> threads(Requests.ThreadsArguments arguments, Response response, IDebugAdapterContext context) {
        ArrayList<Types.Thread> threads = new ArrayList<>();
        try {
            List<ThreadReference> allThreads = context.getDebugSession().getAllThreads();
            context.getThreadCache().resetThreads(allThreads);
            allThreads = allThreads.stream().filter((thread) -> !context.getThreadCache().isDeathThread(thread.uniqueID())).toList();
            List<ThreadInfo> jdiThreads = resolveThreadInfos(allThreads, context);
            for (ThreadInfo jdiThread : jdiThreads) {
                threads.add(new Types.Thread(jdiThread.thread.uniqueID(), "Thread [" + jdiThread.name + "]"));
            }
        } catch (ObjectCollectedException | CancellationException | CompletionException ex) {
            // allThreads may throw VMDisconnectedException when VM terminates and thread.name() may throw ObjectCollectedException
            // when the thread is exiting.
        }
        response.body = new Responses.ThreadsResponseBody(threads);
        return CompletableFuture.completedFuture(response);
    }

    private static List<ThreadInfo> resolveThreadInfos(List<ThreadReference> allThreads, IDebugAdapterContext context) {
        List<ThreadInfo> threadInfos = new ArrayList<>(allThreads.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ThreadReference thread : allThreads) {
            ThreadInfo threadInfo = new ThreadInfo(thread);
            long threadId = thread.uniqueID();
            if (context.getThreadCache().getThreadName(threadId) != null) {
                threadInfo.name = context.getThreadCache().getThreadName(threadId);
            } else {
                if (context.asyncJDWP()) {
                    futures.add(AsyncJdwpUtils.runAsync(() -> {
                        threadInfo.name = threadInfo.thread.name();
                        context.getThreadCache().setThreadName(threadId, threadInfo.name);
                    }));
                } else {
                    threadInfo.name = threadInfo.thread.name();
                    context.getThreadCache().setThreadName(threadId, threadInfo.name);
                }
            }

            threadInfos.add(threadInfo);
        }

        AsyncJdwpUtils.await(futures);
        return threadInfos;
    }

    private CompletableFuture<Response> pause(Requests.PauseArguments arguments, Response response, IDebugAdapterContext context) {
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            context.getStepResultManager().removeMethodResult(arguments.threadId);
            thread.suspend();
            context.getProtocolServer().sendEvent(new Events.StoppedEvent("pause", arguments.threadId));
        } else {
            context.getStepResultManager().removeAllMethodResults();
            context.getDebugSession().suspend();
            context.getProtocolServer().sendEvent(new Events.StoppedEvent("pause", arguments.threadId, true));
        }
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> resume(Requests.ContinueArguments arguments, Response response, IDebugAdapterContext context) {
        boolean allThreadsContinued = true;
        ThreadReference thread = context.getThreadCache().getThread(arguments.threadId);
        if (thread == null) {
            thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        }
        /**
         * See the jdi doc https://docs.oracle.com/javase/7/docs/jdk/api/jpda/jdi/com/sun/jdi/ThreadReference.html#resume(),
         * suspends of both the virtual machine and individual threads are counted. Before a thread will run again, it must
         * be resumed (through ThreadReference#resume() or VirtualMachine#resume()) the same number of times it has been suspended.
         */
        if (thread != null) {
            context.getStepResultManager().removeMethodResult(arguments.threadId);
            context.getExceptionManager().removeException(arguments.threadId);
            allThreadsContinued = false;
            DebugUtility.resumeThread(thread);
            checkThreadRunningAndRecycleIds(thread, context);
        } else {
            context.getStepResultManager().removeAllMethodResults();
            context.getExceptionManager().removeAllExceptions();
            if (context.asyncJDWP()) {
                resumeVMAsync(context.getDebugSession());
            } else {
                context.getDebugSession().resume();
            }
            context.getRecyclableIdPool().removeAllObjects();
        }
        response.body = new Responses.ContinueResponseBody(allThreadsContinued);
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> resumeAll(Requests.ThreadOperationArguments arguments, Response response, IDebugAdapterContext context) {
        context.getExceptionManager().removeAllExceptions();
        if (context.asyncJDWP()) {
            resumeVMAsync(context.getDebugSession());
        } else {
            context.getDebugSession().resume();
        }
        context.getProtocolServer().sendEvent(new Events.ContinuedEvent(arguments.threadId, true));
        context.getRecyclableIdPool().removeAllObjects();
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> resumeOthers(Requests.ThreadOperationArguments arguments, Response response, IDebugAdapterContext context) {
        List<ThreadReference> threads = DebugUtility.getAllThreadsSafely(context.getDebugSession());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ThreadReference thread : threads) {
            if (thread.uniqueID() == arguments.threadId) {
                continue;
            }

            if (context.asyncJDWP()) {
                futures.add(AsyncJdwpUtils.runAsync(() -> resumeThread(thread, context)));
            } else {
                resumeThread(thread, context);
            }
        }
        AsyncJdwpUtils.await(futures);
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> pauseAll(Requests.ThreadOperationArguments arguments, Response response, IDebugAdapterContext context) {
        context.getDebugSession().suspend();
        context.getProtocolServer().sendEvent(new Events.StoppedEvent("pause", arguments.threadId, true));
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> pauseOthers(Requests.ThreadOperationArguments arguments, Response response, IDebugAdapterContext context) {
        List<ThreadReference> threads = DebugUtility.getAllThreadsSafely(context.getDebugSession());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ThreadReference thread : threads) {
            if (thread.uniqueID() == arguments.threadId) {
                continue;
            }

            if (context.asyncJDWP()) {
                futures.add(AsyncJdwpUtils.runAsync(() -> pauseThread(thread, context)));
            } else {
                pauseThread(thread, context);
            }
        }
        AsyncJdwpUtils.await(futures);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Recycle the related ids owned by the specified thread.
     */
    public static void checkThreadRunningAndRecycleIds(ThreadReference thread, IDebugAdapterContext context) {
        try {
            IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
            engine.clearState(thread);
            context.getRecyclableIdPool().removeObjectsByOwner(thread.uniqueID());
        } catch (VMDisconnectedException ex) {
            // isSuspended may throw VMDisconnectedException when the VM terminates
            context.getRecyclableIdPool().removeAllObjects();
        } catch (ObjectCollectedException collectedEx) {
            // isSuspended may throw ObjectCollectedException when the thread terminates
            context.getRecyclableIdPool().removeObjectsByOwner(thread.uniqueID());
        }
    }

    private void resumeVMAsync(IDebugSession debugSession) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ThreadReference tr : DebugUtility.getAllThreadsSafely(debugSession)) {
            futures.add(AsyncJdwpUtils.runAsync(() -> {
                try {
                    while (tr.suspendCount() > 1) {
                        tr.resume();
                    }
                } catch (ObjectCollectedException ex) {
                    // Ignore it if the thread is garbage collected.
                }
            }));
        }

        AsyncJdwpUtils.await(futures);
        debugSession.getVM().resume();
    }

    private void resumeThread(ThreadReference thread, IDebugAdapterContext context) {
        try {
            int suspends = thread.suspendCount();
            if (suspends > 0) {
                long threadId = thread.uniqueID();
                context.getExceptionManager().removeException(threadId);
                DebugUtility.resumeThread(thread, suspends);
                context.getProtocolServer().sendEvent(new Events.ContinuedEvent(threadId));
                checkThreadRunningAndRecycleIds(thread, context);
            }
        } catch (ObjectCollectedException ex) {
            // ignore it.
        }
    }

    private void pauseThread(ThreadReference thread, IDebugAdapterContext context) {
        try {
            if (!thread.isSuspended()) {
                long threadId = thread.uniqueID();
                thread.suspend();
                context.getProtocolServer().sendEvent(new Events.StoppedEvent("pause", threadId));
            }
        } catch (ObjectCollectedException ex) {
            // ignore it if the thread is garbage collected.
        }
    }

    static class ThreadInfo {
        public ThreadReference thread;
        public String name;

        public ThreadInfo(ThreadReference thread) {
            this.thread = thread;
        }
    }
}
