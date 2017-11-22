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

package com.microsoft.java.debug.core.adapter.variables;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public class StoppedState {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private final long version;
    private final ThreadReference thread;
    private List<StackFrame> stackFrames;
    private static AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Create a new Stop State.
     * @param thread the JDI thread
     */
    public StoppedState(ThreadReference thread) {
        this.thread = thread;
        version = nextId.getAndIncrement();
        if (thread.isSuspended()) {
            try {
                stackFrames = Collections.unmodifiableList(thread.frames());
            } catch (IncompatibleThreadStateException e) {
                logger.warning("Thread state is changed during retrieving stack frames.");
            }
        }
    }

    public List<StackFrame> getStackFrames() {
        return stackFrames;
    }

    /**
     * Refresh the list of stack frames because the stack frames have been out of date.
     *
     * @param depth the index of stackframe as the return value
     * @return the stackframe at depth, null if the thread is not suspended or the depth is larger than stack frame size.
     */
    public StackFrame refreshStackFrames(int depth) {
        if (thread.isSuspended()) {
            try {
                stackFrames = Collections.unmodifiableList(thread.frames());
                return stackFrames.size() > depth ? stackFrames.get(depth) : null;
            } catch (IncompatibleThreadStateException e) {
                logger.warning("Thread state is changed during retrieving stack frames.");
            }
        }
        return null;
    }

    public long getVersion() {
        return version;
    }

    public ThreadReference getThread() {
        return thread;
    }
}
