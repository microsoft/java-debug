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

import java.util.Map;

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IStackFrameProvider;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public class DefaultStackFrameProvider implements IStackFrameProvider {
    private Map<Long, StackFrame[]> threadStackFrameMap;

    @Override
    public void initialize(IDebugAdapterContext debugContext, Map<String, Object> options) {
        threadStackFrameMap = debugContext.getThreadStackFrameMap();
    }

    @Override
    public StackFrame getStackFrame(ThreadReference thread, int depth) {
        synchronized (threadStackFrameMap) {
            StackFrame[] frames = threadStackFrameMap.get(thread.uniqueID());
            return frames == null || frames.length < depth ? null : frames[depth];
        }
    }

    @Override
    public StackFrame[] getStackFrames(ThreadReference thread, boolean forceUpdate) {
        synchronized (threadStackFrameMap) {
            return threadStackFrameMap.compute(thread.uniqueID(), (key, old) -> {
                if (forceUpdate || old == null) {
                    try {
                        return thread.frames().toArray(new StackFrame[0]);
                    } catch (IncompatibleThreadStateException e) {
                        return new StackFrame[0];
                    }
                } else {
                    return old;
                }
            });
        }
    }
}
