/*******************************************************************************
* Copyright (c) 2017-2019 Microsoft Corporation and others.
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.IWatchpoint;

public class BreakpointManager implements IBreakpointManager {
    private final Logger logger;
    /**
     * A collection of breakpoints registered with this manager.
     */
    private List<IBreakpoint> breakpoints;
    private Map<String, HashMap<String, IBreakpoint>> sourceToBreakpoints;
    private Map<String, IWatchpoint> watchpoints;
    private AtomicInteger nextBreakpointId = new AtomicInteger(1);

    /**
     * Constructor.
     */
    public BreakpointManager(Logger logger) {
        this.logger = logger;
        this.breakpoints = Collections.synchronizedList(new ArrayList<>(5));
        this.sourceToBreakpoints = new HashMap<>();
        this.watchpoints = new HashMap<>();
    }

    @Override
    public IBreakpoint[] setBreakpoints(String source, IBreakpoint[] breakpoints) {
        return setBreakpoints(source, breakpoints, false);
    }

    @Override
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
                    breakpointMap.remove(String.valueOf(breakpoint.getLineNumber()));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, String.format("Remove breakpoint exception: %s", e.toString()), e);
                }
            }
        }
    }

    @Override
    public IBreakpoint[] getBreakpoints() {
        return this.breakpoints.toArray(new IBreakpoint[0]);
    }

    @Override
    public IBreakpoint[] getBreakpoints(String source) {
        HashMap<String, IBreakpoint> breakpointMap = this.sourceToBreakpoints.get(source);
        if (breakpointMap == null) {
            return new IBreakpoint[0];
        }
        return breakpointMap.values().toArray(new IBreakpoint[0]);
    }

    @Override
    public IWatchpoint[] setWatchpoints(IWatchpoint[] changedWatchpoints) {
        List<IWatchpoint> result = new ArrayList<>();
        List<IWatchpoint> toAdds = new ArrayList<>();
        List<IWatchpoint> toRemoves = new ArrayList<>();

        Set<String> visitedKeys = new HashSet<>();
        for (IWatchpoint change : changedWatchpoints) {
            if (change == null) {
                result.add(change);
                continue;
            }

            String key = getWatchpointKey(change);
            IWatchpoint cache = watchpoints.get(key);
            if (cache != null && Objects.equals(cache.accessType(), change.accessType())) {
                visitedKeys.add(key);
                result.add(cache);
            } else {
                toAdds.add(change);
                result.add(change);
            }
        }

        for (IWatchpoint cache : watchpoints.values()) {
            if (!visitedKeys.contains(getWatchpointKey(cache))) {
                toRemoves.add(cache);
            }
        }

        for (IWatchpoint toRemove : toRemoves) {
            try {
                // Destroy the watch point on the debugee VM.
                toRemove.close();
                this.watchpoints.remove(getWatchpointKey(toRemove));
            } catch (Exception e) {
                logger.log(Level.SEVERE, String.format("Remove the watch point exception: %s", e.toString()), e);
            }
        }

        for (IWatchpoint toAdd : toAdds) {
            toAdd.putProperty("id", this.nextBreakpointId.getAndIncrement());
            this.watchpoints.put(getWatchpointKey(toAdd), toAdd);
        }

        return result.toArray(new IWatchpoint[0]);
    }

    private String getWatchpointKey(IWatchpoint watchpoint) {
        return watchpoint.className() + "#" + watchpoint.fieldName();
    }

    @Override
    public IWatchpoint[] getWatchpoints() {
        return this.watchpoints.values().stream().filter(wp -> wp != null).toArray(IWatchpoint[]::new);
    }
}
