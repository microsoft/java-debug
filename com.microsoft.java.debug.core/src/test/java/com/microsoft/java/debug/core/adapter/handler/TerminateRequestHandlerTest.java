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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.DebugAdapterContext;
import com.microsoft.java.debug.core.adapter.ProviderContext;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.InitializeArguments;
import com.microsoft.java.debug.core.protocol.Requests.TerminateArguments;
import com.microsoft.java.debug.core.protocol.Types.Capabilities;

public class TerminateRequestHandlerTest extends EasyMockSupport {
    @Test
    public void terminateLaunchShouldKeepDebugSessionActive() {
        IDebugSession debugSession = createMock(IDebugSession.class);
        debugSession.terminate();
        replayAll();

        DebugAdapterContext context = new DebugAdapterContext(null, new ProviderContext());
        context.setDebugSession(debugSession);
        Response response = new Response();

        Response result = new TerminateRequestHandler()
                .handle(Command.TERMINATE, new TerminateArguments(), response, context).join();

        assertSame(response, result);
        assertFalse(context.isVmTerminated());
        verifyAll();
    }

    @Test
    public void terminateAttachShouldBeNoOp() {
        IDebugSession debugSession = createMock(IDebugSession.class);
        replayAll();

        DebugAdapterContext context = new DebugAdapterContext(null, new ProviderContext());
        context.setDebugSession(debugSession);
        context.setAttached(true);

        new TerminateRequestHandler()
                .handle(Command.TERMINATE, new TerminateArguments(), new Response(), context).join();

        assertFalse(context.isVmTerminated());
        verifyAll();
    }

    @Test
    public void terminateNoDebugShouldDestroyTrackedProcess() {
        RecordingProcess process = new RecordingProcess();
        DebugAdapterContext context = new DebugAdapterContext(null, new ProviderContext());
        context.setDebuggeeProcess(process);

        new TerminateRequestHandler()
                .handle(Command.TERMINATE, new TerminateArguments(), new Response(), context).join();

        assertTrue(process.isDestroyed());
        assertFalse(context.isVmTerminated());
    }

    @Test
    public void initializeShouldAdvertiseTerminateRequest() {
        DebugAdapterContext context = new DebugAdapterContext(null, new ProviderContext());
        Response response = new Response();

        new InitializeRequestHandler()
                .handle(Command.INITIALIZE, new InitializeArguments(), response, context).join();

        assertTrue(((Capabilities) response.body).supportsTerminateRequest);
    }

    private static class RecordingProcess extends Process {
        private boolean destroyed;

        boolean isDestroyed() {
            return destroyed;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
