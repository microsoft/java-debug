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

import com.sun.jdi.VirtualMachine;

public class EvaluatableBreakpoint extends Breakpoint implements IEvaluatableBreakpoint {
    private Object compiledConditionalExpression = null;
    private Object compiledLogpointExpression = null;

    EvaluatableBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber) {
        super(vm, eventHub, className, lineNumber, 0, null);
    }

    EvaluatableBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount) {
        super(vm, eventHub, className, lineNumber, hitCount, null);
    }

    EvaluatableBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount, String condition) {
        super(vm, eventHub, className, lineNumber, hitCount, condition);
    }

    EvaluatableBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount, String condition, String logMessage) {
        super(vm, eventHub, className, lineNumber, hitCount, condition, logMessage);
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
    }

    @Override
    public void setLogMessage(String logMessage) {
        super.setLogMessage(logMessage);
        setCompiledLogpointExpression(null);
    }
}
