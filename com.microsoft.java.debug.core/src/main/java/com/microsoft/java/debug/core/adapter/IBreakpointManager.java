/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.IMethodBreakpoint;
import com.microsoft.java.debug.core.IWatchpoint;

public interface IBreakpointManager {

    /**
     * Update the breakpoints associated with the source file.
     *
     * @see #setBreakpoints(String, IBreakpoint[], boolean)
     * @param source
     *              source path of breakpoints
     * @param breakpoints
     *              full list of breakpoints that locates in this source file
     * @return the full breakpoint list that locates in the source file
     */
    IBreakpoint[] setBreakpoints(String source, IBreakpoint[] breakpoints);

    /**
     * Update the breakpoints associated with the source file. If the requested breakpoints already registered in the breakpoint manager,
     * reuse the cached one. Otherwise register the requested breakpoint as a new breakpoint. Besides, delete those not existed any more.
     *
     * <p>If the source file is modified, delete all cached breakpoints associated the file first and re-register the new breakpoints.</p>
     *
     * @param source
     *              source path of breakpoints
     * @param breakpoints
     *              full list of breakpoints that locates in this source file
     * @param sourceModified
     *              the source file is modified or not.
     * @return the full breakpoint list that locates in the source file
     */
    IBreakpoint[] setBreakpoints(String source, IBreakpoint[] breakpoints, boolean sourceModified);

    /**
     * Update the watchpoint list. If the requested watchpoint already registered in the breakpoint manager,
     * reuse the cached one. Otherwise register the requested watchpoint as a new watchpoint.
     * Besides, delete those not existed any more.
     *
     * @param watchpoints
     *                 the watchpoints requested by client
     * @return the full registered watchpoints list
     */
    IWatchpoint[] setWatchpoints(IWatchpoint[] watchpoints);

    /**
     * Returns all registered breakpoints.
     */
    IBreakpoint[] getBreakpoints();

    /**
     * Returns the registered breakpoints at the source file.
     */
    IBreakpoint[] getBreakpoints(String source);

    /**
     * Returns all registered watchpoints.
     */
    IWatchpoint[] getWatchpoints();

    /**
     * Returns all the registered method breakpoints.
     */
    IMethodBreakpoint[] getMethodBreakpoints();

    /**
     * Update the method breakpoints list. If the requested method breakpoints
     * already registered in the breakpoint
     * manager, reuse the cached one. Otherwise register the requested method
     * breakpoints as a new method breakpoints.
     * Besides, delete those not existed any more.
     *
     * @param methodBreakpoints
     *                  the method breakpoints requested by client
     * @return the full registered method breakpoints list
     */
    IMethodBreakpoint[] setMethodBreakpoints(IMethodBreakpoint[] methodBreakpoints);

}
