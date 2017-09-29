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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UsageDataStore {
    private ConcurrentLinkedQueue<Object> queue;
    private static final int QUEUE_MAX_SIZE = 10000;

    /**
     * Constructor.
     */
    private UsageDataStore() {
        queue = new ConcurrentLinkedQueue<>();
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
        sessionEntry.put("debugSessionId", UsageDataSession.getSessionGuid());
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
        errorEntry.put("deubgSessionId", UsageDataSession.getSessionGuid());
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

    private synchronized void enqueue(Map<String, String> entry) {
        if (queue.size() > QUEUE_MAX_SIZE) {
            queue.poll();
        }
        if (entry != null) {
            entry.put("timestamp", Instant.now().toString());
            queue.add(entry);
        }
    }
}
