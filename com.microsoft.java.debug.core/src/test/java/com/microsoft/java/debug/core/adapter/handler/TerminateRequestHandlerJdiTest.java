/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.DebugSessionFactory;
import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.DebugAdapterContext;
import com.microsoft.java.debug.core.adapter.ProviderContext;
import com.microsoft.java.debug.core.protocol.Events.DebugEvent;
import com.microsoft.java.debug.core.protocol.IProtocolServer;
import com.microsoft.java.debug.core.protocol.Messages.Request;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.TerminateArguments;
import com.sun.jdi.event.BreakpointEvent;

import io.reactivex.disposables.Disposable;

public class TerminateRequestHandlerJdiTest {
    private static final String PROJECT_NAME = "5.shutdownhook";
    private static final int READY_LINE = 15;
    private static final int SHUTDOWN_HOOK_LINE = 20;

    @Test
    public void terminateShouldKeepDebuggerAttachedForShutdownHookBreakpoint() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));

        boolean suspendAllThreads = DebugSettings.getCurrent().suspendAllThreads;
        DebugSettings.getCurrent().suspendAllThreads = true;
        IDebugSession debugSession = null;
        Process debuggeeProcess = null;
        Disposable subscription = null;
        CountDownLatch readyBreakpoint = new CountDownLatch(1);
        CountDownLatch shutdownHookBreakpoint = new CountDownLatch(1);

        try {
            debugSession = DebugSessionFactory.getDebugSession(PROJECT_NAME, "ShutdownHookTest");
            assertNotNull(debugSession);
            debuggeeProcess = debugSession.process();
            IBreakpoint ready = debugSession.createBreakpoint("ShutdownHookTest", READY_LINE, 0, null, null);
            IBreakpoint shutdownHook = debugSession.createBreakpoint("ShutdownHookTest", SHUTDOWN_HOOK_LINE, 0, null, null);
            subscription = debugSession.getEventHub().breakpointEvents().subscribe(debugEvent -> {
                int line = ((BreakpointEvent) debugEvent.event).location().lineNumber();
                if (line == READY_LINE) {
                    debugEvent.shouldResume = false;
                    readyBreakpoint.countDown();
                } else if (line == SHUTDOWN_HOOK_LINE) {
                    debugEvent.shouldResume = false;
                    shutdownHookBreakpoint.countDown();
                }
            });

            ready.install();
            shutdownHook.install();
            debugSession.start();
            debugSession.resume();
            assertTrue("The debuggee did not reach the ready breakpoint.",
                    readyBreakpoint.await(10, TimeUnit.SECONDS));

            DebugAdapterContext context = new DebugAdapterContext(new NoopProtocolServer(), new ProviderContext());
            context.setDebugSession(debugSession);
            new TerminateRequestHandler()
                    .handle(Command.TERMINATE, new TerminateArguments(), new Response(), context).join();

            assertFalse(context.isVmTerminated());
            assertTrue("The shutdown hook breakpoint was not hit.",
                    shutdownHookBreakpoint.await(10, TimeUnit.SECONDS));
        } finally {
            try {
                dispose(subscription);
                terminate(debuggeeProcess);
                shutdownDebugSession();
            } finally {
                DebugSettings.getCurrent().suspendAllThreads = suspendAllThreads;
            }
        }
    }

    private static void dispose(Disposable subscription) {
        if (subscription == null) {
            return;
        }
        try {
            subscription.dispose();
        } catch (RuntimeException ex) {
            System.err.println("Failed to dispose the breakpoint subscription: " + ex.getMessage());
        }
    }

    private static void terminate(Process debuggeeProcess) {
        if (debuggeeProcess == null) {
            return;
        }
        try {
            if (debuggeeProcess.isAlive()) {
                debuggeeProcess.destroyForcibly();
            }
            if (!debuggeeProcess.waitFor(5, TimeUnit.SECONDS)) {
                System.err.println("The shutdown-hook test process did not exit within five seconds.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for the shutdown-hook test process to exit.");
        } catch (RuntimeException ex) {
            System.err.println("Failed to terminate the shutdown-hook test process: " + ex.getMessage());
        }
    }

    private static void shutdownDebugSession() {
        try {
            DebugSessionFactory.shutdownDebugSession(PROJECT_NAME);
        } catch (Exception ex) {
            System.err.println("Failed to shut down the test debug session: " + ex.getMessage());
        }
    }

    private static class NoopProtocolServer implements IProtocolServer {
        @Override
        public CompletableFuture<Response> sendRequest(Request request) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Response> sendRequest(Request request, long timeout) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void sendEvent(DebugEvent event) {
        }

        @Override
        public void sendResponse(Response response) {
        }
    }
}
