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

public interface IEvaluatableBreakpoint {
    boolean containsEvaluatableExpression();

    boolean containsConditionalExpression();

    boolean containsLogpointExpression();

    String getCondition();

    void setCondition(String condition);

    String getLogMessage();

    void setLogMessage(String logMessage);

    /**
     * please use {@link #setCompiledExpression(long, Object)} instead.
     */
    @Deprecated
    void setCompiledConditionalExpression(Object compiledExpression);

    /**
     * please use {@link #getCompiledExpression(long)} instead.
     */
    @Deprecated
    Object getCompiledConditionalExpression();

    /**
     * please use {@link #setCompiledExpression(long, Object)} instead.
     */
    @Deprecated
    void setCompiledLogpointExpression(Object compiledExpression);

    /**
     * please use {@link #getCompiledExpression(long)} instead.
     */
    @Deprecated
    Object getCompiledLogpointExpression();

    /**
     * Sets the compiled expression for a thread.
     *
     * @param threadId - thread the breakpoint is hit in
     * @param compiledExpression - associated compiled expression
     */
    void setCompiledExpression(long threadId, Object compiledExpression);

    /**
     * Returns existing compiled expression for the given thread or
     * <code>null</code>.
     *
     * @param threadId thread the breakpoint was hit in
     * @return compiled expression or <code>null</code>
     */
    Object getCompiledExpression(long threadId);
}
