/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public class StackFrameManager implements IStackFrameManager {
    private Map<Long, StackFrame[]> threadStackFrameMap = new HashMap<>();

    @Override
    public synchronized StackFrame getStackFrame(StackFrameReference ref) {
        ThreadReference thread = ref.getThread();
        int depth = ref.getDepth();
        StackFrame[] frames = threadStackFrameMap.get(thread.uniqueID());
        return frames == null || frames.length < depth ? null : frames[depth];
    }

    @Override
    public synchronized StackFrame[] reloadStackFrames(ThreadReference thread) {
        return threadStackFrameMap.compute(thread.uniqueID(), (key, old) -> {
            try {
                if (old == null || old.length == 0) {
                    return thread.frames().toArray(new StackFrame[0]);
                } else {
                    return thread.frames(0, old.length).toArray(new StackFrame[0]);
                }
            } catch (IncompatibleThreadStateException e) {
                return new StackFrame[0];
            }
        });
    }

    @Override
    public synchronized StackFrame[] reloadStackFrames(ThreadReference thread, int start, int length) {
        long threadId = thread.uniqueID();
        StackFrame[] old = threadStackFrameMap.get(threadId);
        try {
            StackFrame[] newFrames = thread.frames(start, length).toArray(new StackFrame[0]);
            if (old == null || (start == 0 && length == old.length)) {
                threadStackFrameMap.put(threadId, newFrames);
            } else {
                int maxLength = Math.max(old.length, start + length);
                StackFrame[] totalFrames = new StackFrame[maxLength];
                System.arraycopy(old, 0, totalFrames, 0, old.length);
                System.arraycopy(newFrames, 0, totalFrames, start, length);
                threadStackFrameMap.put(threadId, totalFrames);
            }

            return newFrames;
        } catch (IncompatibleThreadStateException | IndexOutOfBoundsException  e) {
            return new StackFrame[0];
        }
    }
}
