package com.microsoft.java.debug.core.adapter;


public interface IEvaluationListener {

    /**
     * Notifies this listener that an evaluation has completed, with the given
     * result.
     *
     * @param result
     *            The result from the evaluation
     * @see IEvaluationResult
     */
    public void evaluationComplete(String result);
}
