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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.IBreakpoint;

public class BreakpointManager {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    /**
     * A collection of breakpoints registered with this manager.
     */
    private List<IBreakpoint> breakpoints;
    private HashMap<String, HashMap<String, IBreakpoint>> sourceToBreakpoints;
    private AtomicInteger nextBreakpointId = new AtomicInteger(1);
    // BreakpointManager is the owner class of the breakpoint to compiled expression map, it will remove
    // the breakpoint from this map if the breakpoint is removed or its condition is changed
    private Map<IBreakpoint, Object> breakpointExpressionMap = new HashMap<>();

    /**
     * Constructor.
     */
    public BreakpointManager() {
        this.breakpoints = Collections.synchronizedList(new ArrayList<>(5));
        this.sourceToBreakpoints = new HashMap<>();
    }

    /**
     * Adds breakpoints to breakpoint manager.
     * Deletes all breakpoints that are no longer listed.
     * @param source
     *              source path of breakpoints
     * @param breakpoints
     *              full list of breakpoints that locates in this source file
     * @return the full breakpoint list that locates in the source file
     */
    public IBreakpoint[] setBreakpoints(String source, IBreakpoint[] breakpoints) {
        return setBreakpoints(source, breakpoints, false);
    }

    /**
     * Adds breakpoints to breakpoint manager.
     * Deletes all breakpoints that are no longer listed.
     * In the case of modified source, delete everything.
     * @param source
     *              source path of breakpoints
     * @param breakpoints
     *              full list of breakpoints that locates in this source file
     * @param sourceModified
     *              the source file are modified or not.
     * @return the full breakpoint list that locates in the source file
     */
    public IBreakpoint[] setBreakpoints(String source, IBreakpoint[] breakpoints, boolean sourceModified) {
        List<IBreakpoint> result = new ArrayList<>();
        HashMap<String, IBreakpoint> breakpointMap = this.sourceToBreakpoints.get(source);
        // When source file is modified, delete all previously added breakpoints.
        if (sourceModified && breakpointMap != null) {
            for (IBreakpoint bp : breakpointMap.values()) {
                try {
                    // Destroy the breakpoint on the debugee VM.
                    bp.close();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, String.format("Remove breakpoint exception: %s", e.toString()), e);
                }
                breakpointExpressionMap.remove(bp);
                this.breakpoints.remove(bp);
            }
            this.sourceToBreakpoints.put(source, null);
            breakpointMap = null;
        }
        if (breakpointMap == null) {
            breakpointMap = new HashMap<>();
            this.sourceToBreakpoints.put(source, breakpointMap);
        }

        // Compute the breakpoints that are newly added.
        List<IBreakpoint> toAdd = new ArrayList<>();
        List<Integer> visitedLineNumbers = new ArrayList<>();
        for (IBreakpoint breakpoint : breakpoints) {
            IBreakpoint existed = breakpointMap.get(String.valueOf(breakpoint.getLineNumber()));
            if (existed != null) {
                result.add(existed);
                visitedLineNumbers.add(existed.getLineNumber());
                continue;
            } else {
                result.add(breakpoint);
            }
            toAdd.add(breakpoint);
        }

        // Compute the breakpoints that are no longer listed.
        List<IBreakpoint> toRemove = new ArrayList<>();
        for (IBreakpoint breakpoint : breakpointMap.values()) {
            if (!visitedLineNumbers.contains(breakpoint.getLineNumber())) {
                toRemove.add(breakpoint);
            }
        }

        removeBreakpointsInternally(source, toRemove.toArray(new IBreakpoint[0]));
        addBreakpointsInternally(source, toAdd.toArray(new IBreakpoint[0]));

        return result.toArray(new IBreakpoint[0]);
    }

    private void addBreakpointsInternally(String source, IBreakpoint[] breakpoints) {
        Map<String, IBreakpoint> breakpointMap = this.sourceToBreakpoints.computeIfAbsent(source, k -> new HashMap<>());

        if (breakpoints != null && breakpoints.length > 0) {
            for (IBreakpoint breakpoint : breakpoints) {
                breakpoint.putProperty("id", this.nextBreakpointId.getAndIncrement());
                this.breakpoints.add(breakpoint);
                breakpointMap.put(String.valueOf(breakpoint.getLineNumber()), breakpoint);
            }
        }
    }

    /**
     * Removes the specified breakpoints from breakpoint manager.
     */
    private void removeBreakpointsInternally(String source, IBreakpoint[] breakpoints) {
        Map<String, IBreakpoint> breakpointMap = this.sourceToBreakpoints.get(source);
        if (breakpointMap == null || breakpointMap.isEmpty() || breakpoints.length == 0) {
            return;
        }

        for (IBreakpoint breakpoint : breakpoints) {
            if (this.breakpoints.contains(breakpoint)) {
                try {
                    // Destroy the breakpoint on the debugee VM.
                    breakpoint.close();
                    this.breakpoints.remove(breakpoint);
                    breakpointExpressionMap.remove(breakpoint);
                    breakpointMap.remove(String.valueOf(breakpoint.getLineNumber()));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, String.format("Remove breakpoint exception: %s", e.toString()), e);
                }
            }
        }
    }

    public IBreakpoint[] getBreakpoints() {
        return this.breakpoints.toArray(new IBreakpoint[0]);
    }

    /**
     * Gets the registered breakpoints at the source file.
     */
    public IBreakpoint[] getBreakpoints(String source) {
        HashMap<String, IBreakpoint> breakpointMap = this.sourceToBreakpoints.get(source);
        if (breakpointMap == null) {
            return new IBreakpoint[0];
        }
        return breakpointMap.values().toArray(new IBreakpoint[0]);
    }


    /**
     * Get the compiled expression map with breakpoint, it will be used in JdtEvaluationProvider#evaluateForBreakpoint for storing
     * the compiled expression when the first time this conditional breakpoint is hit.
     *
     * @return the compiled expression map
     */
    public Map<IBreakpoint, Object> getBreakpointExpressionMap() {
        return breakpointExpressionMap;
    }

    /**
     *  Update the condition for the specified breakpoint, and clear the compiled expression for the breakpoint.
     *
     * @param breakpoint the conditional breakpoint
     * @param newCondition the new condition to be used.
     */
    public void updateConditionCompiledExpression(IBreakpoint breakpoint, String newCondition) {
        breakpoint.setCondition(newCondition);
        breakpointExpressionMap.remove(breakpoint);
    }

    public void updateLogMessageCompiledExpression(IBreakpoint breakpoint, String newLogMessage) {
        breakpoint.setLogMessage(newLogMessage);
        breakpointExpressionMap.remove(breakpoint);
    }

    /**
     * Cleanup all breakpoints and reset the breakpoint id counter.
     */
    public void reset() {
        this.sourceToBreakpoints.clear();
        this.breakpoints.clear();
        this.nextBreakpointId.set(1);
    }
}
