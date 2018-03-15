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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.IBreakpoint;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;

/**
 * An evaluation engine performs an evaluation of a code snippet or expression
 * in a specified thread of a debug target. An evaluation engine is associated
 * with a specific debug target and Java project on creation.
 */
public interface IEvaluationProvider extends IProvider {
    /**
     * This method provides the event hub the ability to exclude the breakpoint event raised during evaluation.
     * @param thread the thread to be checked against evaluation work.
     * @return whether or not the thread is performing evaluation
     */
    boolean isInEvaluation(ThreadReference thread);

    /**
     * Evaluate the expression at the given thread and stack frame depth, return the promise which is to be resolved/rejected when
     * the evaluation finishes.
     *
     * @param expression The expression to be evaluated
     * @param thread The jdi thread to the expression will be executed at
     * @param depth The depth of stackframe of the stopped thread
     * @return the evaluation result future
     */
    CompletableFuture<Value> evaluate(String expression, ThreadReference thread, int depth);

    /**
     * Evaluate the conditional breakpoint at the given thread and return the promise which is to be resolved/rejected when
     * the evaluation finishes. The breakpointExpressionMap value should be managed by this IEvaluationProvider, avoid duplicate compilation
     * on the same query when the conditional breakpoint is set inside a large loop, when the breakpoint is removed or the condition is changed,
     * the external owner of breakpointExpressionMap must remove the related map entry.
     *
     * @param breakpoint The conditional breakpoint
     * @param thread The jdi thread to the expression will be executed at
     * @param breakpointExpressionMap The map has breakpoint as the key and the compiled expression object for next evaluation use.
     * @return the evaluation result future
     */
    CompletableFuture<Value> evaluateForBreakpoint(IBreakpoint breakpoint, ThreadReference thread, Map<IBreakpoint, Object> breakpointExpressionMap);


    /**
     * Call this method when the thread is to be resumed by user, it will first cancel ongoing evaluation tasks on specified thread and
     * ensure the inner states is cleaned.
     *
     * @param thread the JDI thread reference where the evaluation task is executing at
     */
    void clearState(ThreadReference thread);
}
