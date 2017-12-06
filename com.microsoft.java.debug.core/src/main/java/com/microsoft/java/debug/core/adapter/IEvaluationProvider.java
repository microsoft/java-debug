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

import java.util.concurrent.CompletableFuture;

import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;

/**
 * An evaluation engine performs an evaluation of a code snippet or expression
 * in a specified thread of a debug target. An evaluation engine is associated
 * with a specific debug target and Java project on creation.
 *
 * @see IEvaluationListener
 * @since 4.0
 */
public interface IEvaluationProvider extends IProvider {
    /**
     * This method provides the event hub the ability to exclude the breakpoint event raised during evaluation.
     * @param thread the thread to be checked against evaluation work.
     * @return whether or not the thread is performing evaluation
     */
    boolean isInEvaluation(ThreadReference thread);

    /**
     * Evaluate the expression at the given project and thread and stack frame depth, the promise is to be resolved/rejected when
     * the evaluation finishes.
     *
     * @param projectName The java project which provides resolve class used in the expression
     * @param expression The expression to be evaluated
     * @param sf The stack frame of the evaluation task
     * @return the evaluation result
     */
    CompletableFuture<Value> eval(String projectName, String expression, StackFrame sf);


    /**
     * Cancel ongoing evaluation tasks on specified thread.
     * @param thread the jdi thread
     */
    void cancelEvaluation(ThreadReference thread);

}
