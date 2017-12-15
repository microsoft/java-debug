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

import com.sun.jdi.ThreadReference;

public class StackFrameProxy {
    private final int depth;
    private final int hash;
    private final ThreadReference thread;

    /**
     * Create a wrapper of JDI stackframe to keep only the owning thread and the depth.
     *
     * @param thread the jdi thread.
     * @param depth
     *            the index of this stackframe inside all frames inside one stopped
     *            thread
     */
    public StackFrameProxy(ThreadReference thread, int depth) {
        if (thread == null) {
            throw new NullPointerException("'thread' should not be null for StackFrameProxy");
        }

        if (depth < 0) {
            throw new IllegalArgumentException("'depth' should not be zero or an positive integer.");
        }
        this.thread = thread;
        this.depth = depth;
        hash = Long.hashCode(thread.hashCode()) + depth;
    }

    public int getDepth() {
        return depth;
    }

    public ThreadReference getThread() {
        return thread;
    }


    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        StackFrameProxy sf = (StackFrameProxy) obj;
        return thread.equals(sf.thread) && depth == sf.depth;
    }

}
