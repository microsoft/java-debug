package com.microsoft.java.debug.core.adapter;

import com.microsoft.java.debug.core.protocol.Requests;
import com.sun.jdi.Method;

public interface IStepFilterProvider extends IProvider {
    boolean skip(Method method, Requests.StepFilters filters);
}
