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

import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.adapter.stacktrace.DecodedMethod;
import com.microsoft.java.debug.core.adapter.stacktrace.DecodedVariable;
import com.microsoft.java.debug.core.adapter.stacktrace.JavaMethod;
import com.microsoft.java.debug.core.adapter.stacktrace.JavaLocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.LocalVariable;
import org.apache.commons.lang3.ArrayUtils;
import java.util.Optional;

public class StackTraceProvider implements IStackTraceProvider {
    @Override
    public boolean skipOver(Method method, Requests.StepFilters filters) {
        if (!isConfigured(filters)) {
            return false;
        }
        return (filters.skipStaticInitializers && method.isStaticInitializer())
                || (filters.skipSynthetics && method.isSynthetic())
                || (filters.skipConstructors && method.isConstructor());
    }

    @Override
    public boolean skipOut(Location previousLocation, Method method) {
        return false;
    }


    @Override
    public DecodedMethod decode(Method method) {
        return new JavaMethod(method);
    }

    @Override
    public DecodedVariable decode(LocalVariable variable) {
        return new JavaLocalVariable(variable);
    }

    private boolean isConfigured(Requests.StepFilters filters) {
        if (filters == null) {
            return false;
        }
        return ArrayUtils.isNotEmpty(filters.allowClasses) || ArrayUtils.isNotEmpty(filters.skipClasses)
                || ArrayUtils.isNotEmpty(filters.classNameFilters) || filters.skipConstructors
                || filters.skipStaticInitializers || filters.skipSynthetics;
    }
}
