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

package com.microsoft.java.debug.plugin.internal;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.microsoft.java.debug.core.Log;
import com.microsoft.java.debug.core.adapter.ProtocolServer;

public class JavaDebugServer implements IDebugServer {
    private static JavaDebugServer singletonInstance;

    private ServerSocket serverSocket = null;
    private boolean isStarted = false;
    private ExecutorService executor = null;

    private JavaDebugServer() {
        try {
            serverSocket = new ServerSocket(0, 1);
        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("Failed to create Java Debug Server: %s", e.toString()), e);
        }
    }

    /**
     * Gets the single instance of JavaDebugServer.
     * @return the JavaDebugServer instance
     */
    public static synchronized IDebugServer getInstance() {
        if (singletonInstance == null) {
            singletonInstance = new JavaDebugServer();
        }
        return singletonInstance;
    }

    /**
     * Gets the server port.
     */
    @Override
    public synchronized int getPort() {
        if (serverSocket != null) {
            return serverSocket.getLocalPort();
        }
        return -1;
    }

    /**
     * Starts the server if it's not started yet.
     */
    @Override
    public synchronized void start() {
        if (serverSocket != null && !isStarted) {
            isStarted = true;
            executor = new ThreadPoolExecutor(0, 100, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
            // Execute eventLoop in a new thread.
            new Thread((Runnable) () -> {
                while (true) {
                    try {
                        // Allow server socket to service multiple clients at the same time.
                        // When a request comes in, create a connection thread to process it.
                        // Then the server goes back to listen for new connection request.
                        Socket connection = serverSocket.accept();
                        executor.submit(createConnectionTask(connection));
                    } catch (IOException e) {
                        Log.error(e, "Setup socket connection exception: %s", e.toString());
                        closeServerSocket();
                        // If exception occurs when waiting for new client connection, shut down the connection pool
                        // to make sure no new tasks are accepted. But the previously submitted tasks will continue to run.
                        shutdownConnectionPool(false);
                        return;
                    }
                }
            }, "Java Debug Server").start();
        }
    }

    @Override
    public synchronized void stop() {
        closeServerSocket();
        shutdownConnectionPool(true);
    }

    private synchronized void closeServerSocket() {
        if (serverSocket != null) {
            try {
                Log.info("Close debugserver socket port %d", serverSocket.getLocalPort());
                serverSocket.close();
            } catch (IOException e) {
                Log.error(e, "Close ServerSocket exception: %s", e.toString());
            }
        }
        serverSocket = null;
    }

    private synchronized void shutdownConnectionPool(boolean now) {
        if (executor != null) {
            if (now) {
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
        }
    }

    private Runnable createConnectionTask(Socket connection) {
        return () -> {
            try {
                ProtocolServer protocolServer = new ProtocolServer(connection.getInputStream(), connection.getOutputStream(),
                        JdtProviderContextFactory.createProviderContext());
                // protocol server will dispatch request and send response in a while-loop.
                protocolServer.start();
            } catch (IOException e) {
                Log.error(e, "Socket connection exception: %s", e.toString());
            } finally {
                Log.info("Debug connection closed");
            }
        };
    }

}
