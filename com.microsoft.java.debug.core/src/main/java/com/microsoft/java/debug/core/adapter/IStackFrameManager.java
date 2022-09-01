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
     * Get a jdi stack frame from stack frame reference.
     *
     * @param ref the stackframe reference
     * @return the jdi stackframe
     */
    StackFrame getStackFrame(StackFrameReference ref);

    /**
     * Refresh all stackframes from jdi thread.
     *
     * @param thread the jdi thread
     * @return all the stackframes in the specified thread
     */
    StackFrame[] reloadStackFrames(ThreadReference thread);

    /**
     * Refersh the stackframes starting from the specified depth and length.
     *
     * @param thread the jdi thread
     * @param start the index of the first frame to refresh. Index 0 represents the current frame.
     * @param length the number of frames to refersh
     * @return the refreshed stackframes
     */
    StackFrame[] reloadStackFrames(ThreadReference thread, int start, int length);

    /**
     * Clear the stackframes cache from the specified thread.
     *
     * @param thread the jdi thread
     */
    void clearStackFrames(ThreadReference thread);
}
