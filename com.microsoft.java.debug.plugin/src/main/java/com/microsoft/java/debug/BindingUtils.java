/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.internal.debug.core.breakpoints.LambdaLocationLocatorHelper;

/**
 * Utility methods around working with JDT Bindings.
 */
@SuppressWarnings("restriction")
public final class BindingUtils {
    private BindingUtils() {

    }

    /**
     * Return the method name from the binding using either the
     * {@link IMethodBinding#getKey()} or {@link IMethodBinding#getName()}. The key
     * can be used to find the name of a generated lambda method if the minding
     * represents a lambda method.
     *
     * @param binding the binding to extract the name from.
     * @param fromKey use binging key to resolve the method name.
     * @return the name of the method.
     */
    public static String getMethodName(IMethodBinding binding, boolean fromKey) {
        if (fromKey) {
            return LambdaLocationLocatorHelper.toMethodName(binding);
        } else {
            return binding.getName();
        }
    }

    /**
     * Returns the method signature of the method represented by the binding
     * including the synthetic outer locals.
     *
     * @param binding the binding which the signature must be resolved for.
     * @return the signature or null if the signature could not be resolved.
     */
    public static String toSignature(IMethodBinding binding) {
        return LambdaLocationLocatorHelper.toMethodSignature(binding);
    }

}
