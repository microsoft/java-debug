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

import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public interface IStackFrameManager {
    /**
     * Get a jdi stackframe from jdi thread.
     *
     * @param thread the jdi thread
     * @param depth the depth of stackframe
     * @return the stackframe at the specified depth
     */
    StackFrame getStackFrame(ThreadReference thread, int depth);


    /**
     * Refresh all stackframes from jdi thread.
     *
     * @param thread the jdi thread
     * @return all the stackframes in the specified thread
     */
    StackFrame[] refreshStackFrames(ThreadReference thread);
}
