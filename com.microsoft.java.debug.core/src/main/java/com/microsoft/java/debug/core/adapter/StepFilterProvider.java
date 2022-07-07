package com.microsoft.java.debug.core.adapter;

import com.microsoft.java.debug.core.protocol.Requests;
import com.sun.jdi.Method;
import org.apache.commons.lang3.ArrayUtils;

public class StepFilterProvider implements IStepFilterProvider {
    @Override
    public boolean skip(Method method, Requests.StepFilters filters) {
        if (!isConfigured(filters)) {
            return false;
        }
        return (filters.skipStaticInitializers && method.isStaticInitializer())
                || (filters.skipSynthetics && method.isSynthetic())
                || (filters.skipConstructors && method.isConstructor());
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
