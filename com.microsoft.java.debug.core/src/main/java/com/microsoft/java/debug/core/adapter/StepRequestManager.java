/*******************************************************************************
* Copyright (c) 2020 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.JdiMethodResult;
import com.microsoft.java.debug.core.protocol.Requests;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import io.reactivex.disposables.Disposable;

public class StepRequestManager {
    private final Map<Long, ThreadState> threadStates = Collections.synchronizedMap(new HashMap<>());

    public ThreadState setThreadState(Requests.Command stepType, ThreadReference thread) throws IncompatibleThreadStateException {
        long threadId = thread.uniqueID();
        int stackDepth = thread.frameCount();
        Location location = thread.frame(0).location();
        ThreadState threadState = new ThreadState(threadId, stepType, stackDepth, location);
        threadStates.put(threadId, threadState);
        return threadState;
    }

    public ThreadState getThreadState(long threadId) {
        return threadStates.get(threadId);
    }

    public void setMethodResult(long threadId, JdiMethodResult methodResult) {
        ThreadState threadState = getThreadState(threadId);
        threadState.methodResult = methodResult;
    }

    public JdiMethodResult getMethodResult(long threadId) {
        ThreadState threadState = getThreadState(threadId);
        if (threadState == null) {
            return null;
        }
        return threadState.methodResult;
    }

    public void deletePendingStep(long threadId, EventRequestManager manager) {
        ThreadState threadState = getThreadState(threadId);
        if (threadState != null) {
            threadState.deletePendingStep(manager);
        }
    }

    public void deleteAllPendingSteps(EventRequestManager manager) {
        this.threadStates.forEach((threadId, threadState) -> threadState.deletePendingStep(manager));
    }

    public void removeMethodResult(long threadId) {
        ThreadState threadState = getThreadState(threadId);
        if (threadState == null) {
            return;
        }
        threadState.methodResult = null;
    }

    public void removeAllMethodResults() {
        this.threadStates.forEach((threadId, threadState) -> threadState.methodResult = null);
    }

    public static class ThreadState {
        long threadId;
        Requests.Command stepType;
        StepRequest pendingStepRequest = null;
        MethodExitRequest pendingMethodExitRequest = null;
        int stackDepth;
        Location stepLocation;
        Disposable eventSubscription = null;
        JdiMethodResult methodResult = null;

        public ThreadState(long threadId, Requests.Command stepType, int stackDepth, Location stepLocation) {
            this.threadId = threadId;
            this.stepType = stepType;
            this.stackDepth = stackDepth;
            this.stepLocation = stepLocation;
        }

        public long getThreadId() {
            return threadId;
        }

        public Requests.Command getStepType() {
            return stepType;
        }

        public void setPendingMethodExitRequest(MethodExitRequest pendingMethodExitRequest) {
            this.pendingMethodExitRequest = pendingMethodExitRequest;
        }

        public MethodExitRequest getPendingMethodExitRequest() {
            return pendingMethodExitRequest;
        }

        public void setPendingStepRequest(StepRequest pendingStepRequest) {
            this.pendingStepRequest = pendingStepRequest;
        }

        public int getStackDepth() {
            return stackDepth;
        }

        public StepRequest getPendingStepRequest() {
            return pendingStepRequest;
        }

        public Location getStepLocation() {
            return stepLocation;
        }

        public void setEventSubscription(Disposable eventSubscription) {
            this.eventSubscription = eventSubscription;
        }

        public void deletePendingStep(EventRequestManager manager) {
            deleteStepRequest(manager);
            deleteMethodExitRequest(manager);
            eventSubscription.dispose();
        }

        public void deleteStepRequest(EventRequestManager manager) {
            if (this.pendingStepRequest == null) {
                return;
            }
            DebugUtility.deleteEventRequestSafely(manager, this.pendingStepRequest);
            this.pendingStepRequest = null;
        }

        private void deleteMethodExitRequest(EventRequestManager manager) {
            if (this.pendingMethodExitRequest == null) {
                return;
            }
            DebugUtility.deleteEventRequestSafely(manager, this.pendingMethodExitRequest);
            this.pendingMethodExitRequest = null;
        }
    }
}
