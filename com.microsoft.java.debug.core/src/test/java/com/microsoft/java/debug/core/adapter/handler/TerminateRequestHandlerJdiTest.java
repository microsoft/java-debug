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
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.java.debug.core.DebugSessionFactory;
import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.DebugAdapterContext;
import com.microsoft.java.debug.core.adapter.ProviderContext;
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

        IDebugSession debugSession = DebugSessionFactory.getDebugSession(PROJECT_NAME, "ShutdownHookTest");
        CountDownLatch readyBreakpoint = new CountDownLatch(1);
        CountDownLatch shutdownHookBreakpoint = new CountDownLatch(1);
        IBreakpoint ready = debugSession.createBreakpoint("ShutdownHookTest", READY_LINE, 0, null, null);
        IBreakpoint shutdownHook = debugSession.createBreakpoint("ShutdownHookTest", SHUTDOWN_HOOK_LINE, 0, null, null);
        Disposable subscription = debugSession.getEventHub().breakpointEvents().subscribe(debugEvent -> {
            int line = ((BreakpointEvent) debugEvent.event).location().lineNumber();
            if (line == READY_LINE) {
                debugEvent.shouldResume = false;
                readyBreakpoint.countDown();
            } else if (line == SHUTDOWN_HOOK_LINE) {
                debugEvent.shouldResume = false;
                shutdownHookBreakpoint.countDown();
            }
        });

        try {
            ready.install();
            shutdownHook.install();
            debugSession.start();
            debugSession.resume();
            assertTrue("The debuggee did not reach the ready breakpoint.",
                    readyBreakpoint.await(10, TimeUnit.SECONDS));

            DebugAdapterContext context = new DebugAdapterContext(null, new ProviderContext());
            context.setDebugSession(debugSession);
            new TerminateRequestHandler()
                    .handle(Command.TERMINATE, new TerminateArguments(), new Response(), context).join();

            assertFalse(context.isVmTerminated());
            assertTrue("The shutdown hook breakpoint was not hit.",
                    shutdownHookBreakpoint.await(10, TimeUnit.SECONDS));
            debugSession.resume();
        } finally {
            subscription.dispose();
            DebugSessionFactory.shutdownDebugSession(PROJECT_NAME);
        }
    }
}
