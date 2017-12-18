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

import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public interface IStackFrameManager {
    /**
     * Acquire a stackframe from stack frame manager, for the same thread, only one of the
     * stack frame is available concurrently, the next acquireStackFrame will block until the previous
     * <code>LockedObject</code> is released.
     *
     * @param thread the jdi thread
     * @param depth the depth of stackframe
     * @return the stackframe at the specified depth
     */
    LockedObject<StackFrame> acquireStackFrame(ThreadReference thread, int depth);


    /**
     * Acquire a stackframe from stack frame manager, for the same thread, only one of the
     * stack frame is available concurrently, the next acquireStackFrame will block until the previous
     * <code>LockedObject</code> is released.
     *
     * @param ref the stackframe reference
     */
    LockedObject<StackFrame> acquireStackFrame(StackFrameReference ref);



    /**
     * Refresh all stackframes from jdi thread.
     *
     * @param thread the jdi thread
     * @return all the stackframes in the specified thread
     */
    StackFrame[] reloadStackFrames(ThreadReference thread);
}
