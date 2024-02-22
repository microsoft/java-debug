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

package com.microsoft.java.debug.core.adapter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public class StackFrameManager implements IStackFrameManager {
    private Map<Long, StackFrame[]> threadStackFrameMap = Collections.synchronizedMap(new HashMap<>());
    private Map<Pair<Long, Integer>, Integer> threadStackFrameOffsetMap =
        Collections.synchronizedMap(new HashMap<>());

    @Override
    public StackFrame getStackFrame(StackFrameReference ref) {
        ThreadReference thread = ref.getThread();
        int depth = ref.getDepth();
        StackFrame[] frames = threadStackFrameMap.get(thread.uniqueID());
        return frames == null || frames.length < depth ? null : frames[depth];
    }

    @Override
    public StackFrame[] reloadStackFrames(ThreadReference thread) {
        return threadStackFrameMap.compute(thread.uniqueID(), (key, old) -> {
            try {
                return thread.frames().toArray(new StackFrame[0]);
            } catch (IncompatibleThreadStateException e) {
                return new StackFrame[0];
            }
        });
    }

    @Override
    public void setStackFrameOffset(ThreadReference thread, int frame, int offset) {
        threadStackFrameOffsetMap.put(Pair.of(thread.uniqueID(), frame), offset);
        return;
    }

    @Override
    public int getStackFrameOffset(ThreadReference thread, int frame) {
        Pair<Long, Integer> key = Pair.of(thread.uniqueID(), frame);
        if (threadStackFrameOffsetMap.containsKey(key)) {
            return threadStackFrameOffsetMap.get(key);
        }
        return 0;
    }
}
