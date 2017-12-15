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
     * Evaluate the expression at the given project and thread and stack frame depth, the promise is to be resolved/rejected when
     * the evaluation finishes.
     *
     * @param code The expression to be evaluated
     * @param thread The jdi thread to the expression will be executed at
     * @param depth The depth of stackframe of the stopped thread
     * @return the evaluation result future
     */
    CompletableFuture<Value> evaluate(String code, ThreadReference thread, int depth);


    /**
     * Call this method when the thread is to be resumed by user, it will first cancel ongoing evaluation tasks on specified thread and
     * ensure the inner states is cleaned.
     *
     * @param thread the JDI thread reference where the evaluation task is executing at
     */
    void cleanEvaluateStates(ThreadReference thread);

}
