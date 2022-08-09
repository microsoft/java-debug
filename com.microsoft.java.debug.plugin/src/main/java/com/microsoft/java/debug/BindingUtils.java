/*******************************************************************************
 * Copyright (c) 2017-2020 Microsoft Corporation and others.
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

/**
 * Utility methods around working with JDT Bindings.
 */
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
            String key = binding.getKey();
            return key.substring(key.indexOf('.') + 1, key.indexOf('('));
        } else {
            return binding.getName();
        }
    }

    /**
     * Returns the method signature of the method represented by the binding. Since
     * this implementation use the {@link IMethodBinding#getKey()} to extract the
     * signature from, the method name must be passed in.
     *
     * @param binding the binding which the signature must be resolved for.
     * @param name    the name of the method.
     * @return the signature or null if the signature could not be resolved from the
     *         key.
     */
    public static String toSignature(IMethodBinding binding, String name) {
        // use key for now until JDT core provides a public API for this.
        // "Ljava/util/Arrays;.asList<T:Ljava/lang/Object;>([TT;)Ljava/util/List<TT;>;"
        // "([Ljava/lang/String;)V|Ljava/lang/InterruptedException;"
        if (!binding.getName().equals(name)) {
            throw new IllegalArgumentException("The method name and binding method name doesn't match.");
        }

        String signatureString = binding.getKey();
        if (signatureString != null) {
            int index = signatureString.indexOf(name);
            if (index > -1) {
                int exceptionIndex = signatureString.indexOf("|", signatureString.lastIndexOf(")"));
                if (exceptionIndex > -1) {
                    return signatureString.substring(index + name.length(), exceptionIndex);
                }
                return signatureString.substring(index + name.length());
            }
        }
        return null;
    }
}
