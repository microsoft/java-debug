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

package com.microsoft.java.debug.core.adapter.variables;

import java.util.Objects;

public class VariableProxy {
    private final long threadId;
    private final String scopeName;
    private Object variable;
    private int hashCode;

    /**
     * Create a variable proxy.
     * @param threadId
     *              the context thread id
     * @param scopeName
     *              the scope name
     * @param variable
     *              the variable object
     */
    public VariableProxy(long threadId, String scopeName, Object variable) {
        this.threadId = threadId;
        this.scopeName = scopeName;
        this.variable = variable;
        this.hashCode = (int) (threadId & scopeName.hashCode() & variable.hashCode());
    }

    @Override
    public String toString() {
        return String.format("%s %s", String.valueOf(this.variable), this.scopeName);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VariableProxy)) {
            return false;
        }
        final VariableProxy other = (VariableProxy) o;
        return this.getThreadId() == other.getThreadId()
                && Objects.equals(this.getScope(), other.getScope())
                && Objects.equals(this.getProxiedVariable(), other.getProxiedVariable());
    }

    public long getThreadId() {
        return this.threadId;
    }

    public String getScope() {
        return this.scopeName;
    }

    public Object getProxiedVariable() {
        return this.variable;
    }
}
