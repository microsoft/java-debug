/*******************************************************************************
* Copyright (c) 2017-2019 Microsoft Corporation and others.
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

import com.sun.jdi.ThreadReference;

public class VariableProxy {
    private final ThreadReference thread;
    private final String scopeName;
    private Object variable;
    private int hashCode;

    /**
     * Create a variable reference.
     *
     * @param thread the jdi thread
     * @param scopeName
     *              the scope name
     * @param variable
     *              the variable object
     */
    public VariableProxy(ThreadReference thread, String scopeName, Object variable) {
        this.thread = thread;
        this.scopeName = scopeName;
        this.variable = variable;
        hashCode = Objects.hash(scopeName, thread, variable);
    }

    @Override
    public String toString() {
        return String.format("%s %s", String.valueOf(variable), scopeName);
    }

    public ThreadReference getThread() {
        return thread;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        VariableProxy other = (VariableProxy) obj;
        return Objects.equals(scopeName, other.scopeName) && Objects.equals(getThreadId(), other.getThreadId())
                && Objects.equals(variable, other.variable);
    }

    public long getThreadId() {
        return thread.uniqueID();
    }

    public String getScope() {
        return scopeName;
    }

    public Object getProxiedVariable() {
        return variable;
    }
}
