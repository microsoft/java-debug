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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private Map<Long, ThreadReference> eventThreads = new ConcurrentHashMap<>();
    private Map<Long, Set<String>> decompiledClassesByThread = new HashMap<>();
    private Map<Long, String> threadStoppedReasons = new HashMap<>();

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

        for (ThreadReference thread : eventThreads.values()) {
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
        eventThreads.remove(threadId);
        deathThreads.put(threadId, true);
    }

    public boolean isDeathThread(long threadId) {
        return deathThreads.containsKey(threadId);
    }

    public void addEventThread(ThreadReference thread) {
        eventThreads.put(thread.uniqueID(), thread);
    }

    public void addEventThread(ThreadReference thread, String reason) {
        eventThreads.put(thread.uniqueID(), thread);
        if (reason != null) {
            threadStoppedReasons.put(thread.uniqueID(), reason);
        }
    }

    public void removeEventThread(long threadId) {
        eventThreads.remove(threadId);
    }

    public void clearEventThread() {
        eventThreads.clear();
    }

    /**
     * The visible threads includes:
     * 1. The currently running threads returned by the JDI API
     * VirtualMachine.allThreads().
     * 2. The threads suspended by events such as Breakpoint, Step, Exception etc.
     *
     * The part 2 is mainly for virtual threads, since VirtualMachine.allThreads()
     * does not include virtual threads by default. For those virtual threads
     * that are suspended, we need to show their call stacks in CALL STACK view.
     */
    public List<ThreadReference> visibleThreads(IDebugAdapterContext context) {
        List<ThreadReference> visibleThreads = new ArrayList<>(context.getDebugSession().getAllThreads());
        Set<Long> idSet = new HashSet<>();
        visibleThreads.forEach(thread -> idSet.add(thread.uniqueID()));
        for (ThreadReference thread : eventThreads.values()) {
            if (idSet.contains(thread.uniqueID())) {
                continue;
            }

            idSet.add(thread.uniqueID());
            visibleThreads.add(thread);
        }

        return visibleThreads;
    }

    public Set<String> getDecompiledClassesByThread(long threadId) {
        return decompiledClassesByThread.get(threadId);
    }

    public void setDecompiledClassesByThread(long threadId, Set<String> decompiledClasses) {
        if (decompiledClasses == null || decompiledClasses.isEmpty()) {
            decompiledClassesByThread.remove(threadId);
            return;
        }

        decompiledClassesByThread.put(threadId, decompiledClasses);
    }

    public String getThreadStoppedReason(long threadId) {
        return threadStoppedReasons.get(threadId);
    }

    public void setThreadStoppedReason(long threadId, String reason) {
        if (reason == null) {
            threadStoppedReasons.remove(threadId);
            return;
        }

        threadStoppedReasons.put(threadId, reason);
    }

    public void clearThreadStoppedState(long threadId) {
        threadStoppedReasons.remove(threadId);
        decompiledClassesByThread.remove(threadId);
    }

    public void clearAllThreadStoppedState() {
        threadStoppedReasons.clear();
        decompiledClassesByThread.clear();
    }
}
