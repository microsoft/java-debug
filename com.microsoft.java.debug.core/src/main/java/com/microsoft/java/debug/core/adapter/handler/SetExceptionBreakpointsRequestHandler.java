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

package com.microsoft.java.debug.core.adapter.handler;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.DebugSettings.IDebugSettingChangeListener;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.ExceptionFilters;
import com.microsoft.java.debug.core.protocol.Requests.SetExceptionBreakpointsArguments;
import com.microsoft.java.debug.core.protocol.Types.ExceptionFilterOptions;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.EventRequest;

public class SetExceptionBreakpointsRequestHandler implements IDebugRequestHandler, IDebugSettingChangeListener {
    private IDebugSession debugSession = null;
    private boolean isInitialized = false;
    private boolean notifyCaught = false;
    private boolean notifyUncaught = false;
    private boolean asyncJDWP = false;
    private int suspendModeOnCaught;
    private int suspendModeOnUncaught;

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SETEXCEPTIONBREAKPOINTS);
    }

    @Override
    public synchronized CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Empty debug session.");
        }

        if (!isInitialized) {
            isInitialized = true;
            debugSession = context.getDebugSession();
            asyncJDWP = context.asyncJDWP();
            DebugSettings.addDebugSettingChangeListener(this);
            debugSession.getEventHub().events().subscribe(debugEvent -> {
                if (debugEvent.event instanceof VMDeathEvent
                    || debugEvent.event instanceof VMDisconnectEvent) {
                    DebugSettings.removeDebugSettingChangeListener(this);
                }
            });
        }

        SetExceptionBreakpointsArguments requestArgs = (SetExceptionBreakpointsArguments) arguments;
        String[] filters = requestArgs.filters;

        try {
            this.notifyCaught = false;
            this.notifyUncaught = false;
            if (filters != null) {
                for (String filter : filters) {
                    if (filter.equals(Types.ExceptionBreakpointFilter.CAUGHT_EXCEPTION_FILTER_NAME)) {
                        this.notifyCaught = true;
                    } else if (filter.equals(Types.ExceptionBreakpointFilter.UNCAUGHT_EXCEPTION_FILTER_NAME)) {
                        this.notifyUncaught = true;
                    }
                }
            }
            this.suspendModeOnCaught = EventRequest.SUSPEND_EVENT_THREAD;
            this.suspendModeOnUncaught = EventRequest.SUSPEND_EVENT_THREAD;

            ExceptionFilterOptions[] filterOptions = requestArgs.filterOptions;
            if (filterOptions != null) {
                for (ExceptionFilterOptions filterOption : requestArgs.filterOptions) {
                    if (filterOption.filterId.equals(Types.ExceptionBreakpointFilter.CAUGHT_EXCEPTION_FILTER_NAME)) {
                        this.notifyCaught = true;
                        this.suspendModeOnCaught = AdapterUtils.suspendPolicyFromBreakpointMode(filterOption.mode);
                    } else if (filterOption.filterId.equals(Types.ExceptionBreakpointFilter.UNCAUGHT_EXCEPTION_FILTER_NAME)) {
                        this.notifyUncaught = true;
                        this.suspendModeOnUncaught = AdapterUtils.suspendPolicyFromBreakpointMode(filterOption.mode);
                    }
                }
            }
            setExceptionBreakpoints(context.getDebugSession(), this.notifyCaught, this.notifyUncaught, suspendModeOnCaught, suspendModeOnUncaught);
            return CompletableFuture.completedFuture(response);
        } catch (Exception ex) {
            throw AdapterUtils.createCompletionException(
                String.format("Failed to setExceptionBreakpoints. Reason: '%s'", ex.toString()),
                ErrorCode.SET_EXCEPTIONBREAKPOINT_FAILURE,
                ex);
        }
    }

    private void setExceptionBreakpoints(IDebugSession debugSession, boolean notifyCaught, boolean notifyUncaught,
            int suspendModeOnCaught, int suspendModeOnUncaught) {
        ExceptionFilters exceptionFilters = DebugSettings.getCurrent().exceptionFilters;
        String[] exceptionTypes = (exceptionFilters == null ? null : exceptionFilters.exceptionTypes);
        String[] classFilters = (exceptionFilters == null ? null : exceptionFilters.allowClasses);
        String[] classExclusionFilters = (exceptionFilters == null ? null : exceptionFilters.skipClasses);
        debugSession.setExceptionBreakpoints(notifyCaught, notifyUncaught, suspendModeOnCaught, suspendModeOnUncaught,
                exceptionTypes, classFilters, classExclusionFilters, this.asyncJDWP);
    }

    @Override
    public synchronized void update(DebugSettings oldSettings, DebugSettings newSettings) {
        try {
            if (newSettings != null && newSettings.exceptionFiltersUpdated) {
                setExceptionBreakpoints(debugSession, notifyCaught, notifyUncaught, suspendModeOnCaught, suspendModeOnUncaught);
            }
        } catch (Exception ex) {
            DebugSettings.removeDebugSettingChangeListener(this);
        }
    }
}
