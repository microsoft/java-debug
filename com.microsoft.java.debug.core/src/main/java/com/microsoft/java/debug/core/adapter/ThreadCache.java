/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.jdi.ThreadReference;

public class ThreadCache {
    private List<ThreadReference> allThreads = new ArrayList<>();
    private Map<Long, String> threadNameMap = new ConcurrentHashMap<>();
    private Map<Long, Boolean> deathThreads = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, Boolean> eldest) {
            return this.size() > 100;
        }
    });

    public synchronized void resetThreads(List<ThreadReference> threads) {
        allThreads.clear();
        allThreads.addAll(threads);
    }

    public synchronized List<ThreadReference> getThreads() {
        return allThreads;
    }

    public synchronized ThreadReference getThread(long threadId) {
        for (ThreadReference thread : allThreads) {
            if (threadId == thread.uniqueID()) {
                return thread;
            }
        }

        return null;
    }

    public void setThreadName(long threadId, String name) {
        threadNameMap.put(threadId, name);
    }

    public String getThreadName(long threadId) {
        return threadNameMap.get(threadId);
    }

    public void addDeathThread(long threadId) {
        threadNameMap.remove(threadId);
        deathThreads.put(threadId, true);
    }

    public void removeDeathThread(long threadId) {
        deathThreads.remove(threadId);
    }

    public boolean isDeathThread(long threadId) {
        return deathThreads.containsKey(threadId);
    }
}
