/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

import io.reactivex.disposables.Disposable;

public class EvaluatableBreakpoint extends Breakpoint implements IEvaluatableBreakpoint {
    private IEventHub eventHub = null;
    private Object compiledConditionalExpression = null;
    private Object compiledLogpointExpression = null;
    private Map<Long, Object> compiledExpressions = new HashMap<>();

    EvaluatableBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber) {
        this(vm, eventHub, className, lineNumber, 0, null);
    }

    EvaluatableBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount) {
        this(vm, eventHub, className, lineNumber, hitCount, null);
    }

    EvaluatableBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount,
            String condition) {
        this(vm, eventHub, className, lineNumber, hitCount, condition, null);
    }

    EvaluatableBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount,
            String condition, String logMessage) {
        super(vm, eventHub, className, lineNumber, hitCount, condition, logMessage);
        this.eventHub = eventHub;
    }

    @Override
    public boolean containsEvaluatableExpression() {
        return containsConditionalExpression() || containsLogpointExpression();
    }

    @Override
    public boolean containsConditionalExpression() {
        return StringUtils.isNotBlank(getCondition());
    }

    @Override
    public boolean containsLogpointExpression() {
        return StringUtils.isNotBlank(getLogMessage());
    }

    @Override
    public void setCompiledConditionalExpression(Object compiledExpression) {
        this.compiledConditionalExpression = compiledExpression;
    }

    @Override
    public Object getCompiledConditionalExpression() {
        return compiledConditionalExpression;
    }

    @Override
    public void setCompiledLogpointExpression(Object compiledExpression) {
        this.compiledLogpointExpression = compiledExpression;
    }

    @Override
    public Object getCompiledLogpointExpression() {
        return compiledLogpointExpression;
    }

    @Override
    public void setCondition(String condition) {
        super.setCondition(condition);
        setCompiledConditionalExpression(null);
        compiledExpressions.clear();
    }

    @Override
    public void setLogMessage(String logMessage) {
        super.setLogMessage(logMessage);
        setCompiledLogpointExpression(null);
        compiledExpressions.clear();
    }

    @Override
    public Object getCompiledExpression(long threadId) {
        return compiledExpressions.get(threadId);
    }

    @Override
    public void setCompiledExpression(long threadId, Object compiledExpression) {
        compiledExpressions.put(threadId, compiledExpression);
    }

    @Override
    public CompletableFuture<IBreakpoint> install() {
        Disposable subscription = eventHub.events()
            .filter(debugEvent -> debugEvent.event instanceof ThreadDeathEvent)
            .subscribe(debugEvent -> {
                ThreadReference deathThread = ((ThreadDeathEvent) debugEvent.event).thread();
                compiledExpressions.remove(deathThread.uniqueID());
            });
        super.subscriptions().add(subscription);

        return super.install();
    }
}
