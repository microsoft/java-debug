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

/**
 * Evaluation results are reported to evaluation listeners on the completion of
 * an evaluation. The evaluation may fail but an exception will be supplied
 * indicating the problems.
 * <p>
 * Clients may implement this interface to handle the result or exception.
 * </p>
 *
 * @since 4.0
 */
public interface IEvaluationListener {

    /**
     * Notifies this listener that an evaluation has completed, with the given
     * result.
     *
     * @param result
     *            The result from the evaluation
     * @param exception
     *            The exception raised during evaluation
     * @see IEvaluationResult
     */
    public void evaluationComplete(String result, Exception exception);
}
