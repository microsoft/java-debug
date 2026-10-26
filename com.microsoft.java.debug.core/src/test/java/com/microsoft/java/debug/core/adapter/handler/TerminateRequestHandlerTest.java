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

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.easymock.IMocksControl;
import org.junit.Test;

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.DebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IExceptionManager;
import com.microsoft.java.debug.core.adapter.IStackFrameManager;
import com.microsoft.java.debug.core.adapter.IStepResultManager;
import com.microsoft.java.debug.core.adapter.ProviderContext;
import com.microsoft.java.debug.core.adapter.RecyclableObjectPool;
import com.microsoft.java.debug.core.adapter.ThreadCache;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.IProtocolServer;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.InitializeArguments;
import com.microsoft.java.debug.core.protocol.Requests.TerminateArguments;
import com.microsoft.java.debug.core.protocol.Types.Capabilities;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

public class TerminateRequestHandlerTest extends EasyMockSupport {
    @Test
    public void terminateLaunchShouldResumeAndClearStateBeforeTermination() {
        IMocksControl control = EasyMock.createStrictControl();
        IDebugAdapterContext context = control.createMock(IDebugAdapterContext.class);
        IDebugSession debugSession = control.createMock(IDebugSession.class);
        IStepResultManager stepResultManager = control.createMock(IStepResultManager.class);
        IExceptionManager exceptionManager = control.createMock(IExceptionManager.class);
        RecordingThreadCache threadCache = new RecordingThreadCache();
        IProtocolServer protocolServer = control.createMock(IProtocolServer.class);
        IStackFrameManager stackFrameManager = control.createMock(IStackFrameManager.class);
        RecyclableObjectPool<Long, Object> recyclableIdPool = new RecyclableObjectPool<>();
        int recyclableId = recyclableIdPool.addObject(1L, new Object());
        VirtualMachine vm = control.createMock(VirtualMachine.class);
        Capture<Events.DebugEvent> event = EasyMock.newCapture();
        AtomicBoolean vmResumed = new AtomicBoolean();

        expect(context.isAttached()).andReturn(false);
        expect(context.getDebugSession()).andReturn(debugSession);
        expect(context.getStepResultManager()).andReturn(stepResultManager);
        stepResultManager.removeAllMethodResults();
        expect(context.getExceptionManager()).andReturn(exceptionManager);
        exceptionManager.removeAllExceptions();
        expect(context.getThreadCache()).andReturn(threadCache);
        expect(context.getThreadCache()).andReturn(threadCache);
        expect(context.getThreadCache()).andReturn(threadCache);
        expect(context.getDebugSession()).andReturn(debugSession);
        expect(debugSession.getVM()).andReturn(vm);
        vm.resume();
        expectLastCall().andAnswer(() -> {
            vmResumed.set(true);
            return null;
        });
        expect(context.getProtocolServer()).andReturn(protocolServer);
        protocolServer.sendEvent(capture(event));
        expect(context.getStackFrameManager()).andReturn(stackFrameManager);
        stackFrameManager.clearStackFrames();
        expect(context.getRecyclableIdPool()).andReturn(recyclableIdPool);
        debugSession.terminate();
        expectLastCall().andAnswer(() -> {
            assertTrue("The VM must be resumed before it is terminated.", vmResumed.get());
            assertTrue("Stopped-thread state must be cleared before termination.", threadCache.stoppedStateCleared);
            assertTrue("Event-thread state must be cleared before termination.", threadCache.eventThreadsCleared);
            return null;
        });
        control.replay();

        Response response = new Response();
        Response result = new TerminateRequestHandler()
                .handle(Command.TERMINATE, new TerminateArguments(), response, context).join();

        assertSame(response, result);
        assertTrue(event.getValue() instanceof Events.ContinuedEvent);
        assertTrue(((Events.ContinuedEvent) event.getValue()).allThreadsContinued);
        assertNull(recyclableIdPool.getObjectById(recyclableId));
        control.verify();
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

    private static class RecordingThreadCache extends ThreadCache {
        private boolean stoppedStateCleared;
        private boolean eventThreadsCleared;

        @Override
        public void clearAllThreadStoppedState() {
            stoppedStateCleared = true;
        }

        @Override
        public List<ThreadReference> visibleThreads(IDebugAdapterContext context) {
            return Collections.emptyList();
        }

        @Override
        public void clearEventThread() {
            eventThreadsCleared = true;
        }
    }
}
