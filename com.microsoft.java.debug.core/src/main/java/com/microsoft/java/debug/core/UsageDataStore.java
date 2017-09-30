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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.microsoft.java.debug.core.adapter.JsonUtils;

public class UsageDataStore {
    private ConcurrentLinkedQueue<Object> queue;
    private static final int QUEUE_MAX_SIZE = 10000;
    private static final String DEBUG_SESSION_ID_NAME = "debugSessionId";
    private static final String DESCRIPTION_NAME = "description";
    private static final String ERROR_MESSAGE_NAME = "message";
    private static final String STACKTRACE_NAME = "stackTrace";
    private static final String SCOPE_NAME = "scope";
    private static final String TIMESTAMP_NAME = "timestamp";

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
        sessionEntry.put(SCOPE_NAME, "session");
        sessionEntry.put(DEBUG_SESSION_ID_NAME, UsageDataSession.getSessionGuid());
        if (desc != null) {
            sessionEntry.put(DESCRIPTION_NAME, desc);
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
        errorEntry.put(SCOPE_NAME, "exception");
        errorEntry.put(DEBUG_SESSION_ID_NAME, UsageDataSession.getSessionGuid());
        if (desc != null) {
            errorEntry.put(DESCRIPTION_NAME, desc);
        }
        if (th != null) {
            errorEntry.put(ERROR_MESSAGE_NAME, th.getMessage());
            errorEntry.put(STACKTRACE_NAME, JsonUtils.toJson(th.getStackTrace()));
        }
        enqueue(errorEntry);
    }

    private synchronized void enqueue(Map<String, String> entry) {
        if (queue.size() > QUEUE_MAX_SIZE) {
            queue.poll();
        }
        if (entry != null) {
            entry.put(TIMESTAMP_NAME, Instant.now().toString());
            queue.add(entry);
        }
    }
}
