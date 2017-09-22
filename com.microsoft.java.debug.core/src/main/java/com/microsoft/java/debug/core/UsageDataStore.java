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

package com.microsoft.java.debug.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

public class UsageDataStore {
    private ConcurrentLinkedQueue<Object> queue;
    private AtomicReference<String> sessionGuid;
    private static final int QUEUE_MAX_SIZE = 10000;

    /**
     * Constructor.
     */
    public UsageDataStore() {
        queue = new ConcurrentLinkedQueue<>();
        sessionGuid = new AtomicReference<>();
        sessionGuid.set(null);
    }

    private static final class SingletonHolder {
        private static final UsageDataStore INSTANCE = new UsageDataStore();
    }

    /**
     * Fetch all pending user data records
     * @return List of user data Object.
     */
    public synchronized Object[] fetchAll() {
        Object[] ret = queue.toArray();
        queue.clear();
        return ret;
    }

    public static UsageDataStore getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * Log user data Object into the queue.
     */
    public void logSessionData(String desc, Map<String, String> props) {
        if (queue == null) {
            return;
        }
        Map<String, String> sessionEntry = new HashMap<>();
        sessionEntry.put("scope", "session");
        sessionEntry.put("sessionId", sessionGuid.get());
        if (desc != null) {
            sessionEntry.put("description", desc);
        }
        if (props != null) {
            sessionEntry.putAll(props);
        }
        enqueue(sessionEntry);
    }

    /**
     * Log Exception details into queue.
     */
    public void logErrorData(String desc, Throwable th) {
        if (queue == null) {
            return;
        }
        Map<String, String> errorEntry = new HashMap<>();
        errorEntry.put("scope", "exception");
        errorEntry.put("deubgSessionId", sessionGuid.get());
        if (desc != null) {
            errorEntry.put("description", desc);
        }
        if (th != null) {
            errorEntry.put("message", th.getMessage());
            StringWriter sw = new StringWriter();
            th.printStackTrace(new PrintWriter(sw));
            errorEntry.put("stackTrace", sw.toString());
        }
        enqueue(errorEntry);
    }

    /**
     * Assign a GUID for debug session.
     */
    public String createSessionGUID() {
        if (sessionGuid.get() != null) {
            // TODO: last session not disconnected.
        } else {
            sessionGuid.set(UUID.randomUUID().toString());
        }
        return sessionGuid.get();
    }

    public void resetSessionGUID() {
        sessionGuid.set(null);
    }

    private synchronized void enqueue(Object object) {
        if (queue.size() > QUEUE_MAX_SIZE) {
            queue.poll();
        }
        queue.add(object);
    }
}
