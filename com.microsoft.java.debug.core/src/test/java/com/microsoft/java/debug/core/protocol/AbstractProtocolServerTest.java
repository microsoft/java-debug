package com.microsoft.java.debug.core.protocol;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertFalse;

public class AbstractProtocolServerTest extends EasyMockSupport {

    @Test
    public void testCanInterruptRunningServer() throws InterruptedException {
        InputStream input = new BlockedInputStream();
        OutputStream output = new DummyOutputStream();

        AbstractProtocolServer server = createMockBuilder(AbstractProtocolServer.class)
                .withConstructor(InputStream.class, OutputStream.class)
                .withArgs(input, output)
                .createMock();

        // when
        Thread worker = new Thread(server::run);
        worker.start();
        worker.interrupt();

        // then
        worker.join(1000);
        assertFalse("Could not interrupt the server", worker.isAlive());
    }

    private class BlockedInputStream extends InputStream {
        @Override
        public synchronized int read() {
            try {
                wait();
                return -1;
            } catch (InterruptedException e) {
                return read(); // never unblock
            }
        }
    }

    private class DummyOutputStream extends OutputStream {
        @Override
        public void write(int b) {
        }
    }
}