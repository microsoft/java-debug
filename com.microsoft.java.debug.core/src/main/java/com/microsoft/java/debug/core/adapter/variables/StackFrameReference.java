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

package com.microsoft.java.debug.core.adapter.variables;

import com.microsoft.java.debug.core.protocol.Types.Source;
import com.sun.jdi.ThreadReference;

public class StackFrameReference {
    private final int depth;
    private final int hash;
    private final ThreadReference thread;
    private Source source;

    /**
     * Create a wrapper of JDI stackframe to keep the immutable properties of a stackframe, IStackFrameManager will use
     * these properties to construct a jdi stackframe.
     *
     * @param thread the jdi thread.
     * @param depth
     *            the index of this stackframe inside all frames inside one stopped
     *            thread
     */
    public StackFrameReference(ThreadReference thread, int depth) {
        if (thread == null) {
            throw new NullPointerException("'thread' should not be null for StackFrameReference");
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

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
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
        StackFrameReference sf = (StackFrameReference) obj;
        return thread.equals(sf.thread) && depth == sf.depth;
    }

}
