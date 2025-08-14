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

package com.microsoft.java.debug.core.adapter.formatter;

import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.ARRAY;

import java.util.Map;
import java.util.function.BiFunction;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

/**
 * Formatter for array objects in the Java Debug Interface (JDI).
 * Extends the ObjectFormatter to provide string representations of array objects,
 * incorporating the array length into the format.
 */
public class ArrayObjectFormatter extends ObjectFormatter {

    /**
     * Constructs an ArrayObjectFormatter with a specific type string function.
     *
     * @param typeStringFunction A function that generates a string representation based on the JDI type and options.
     */
    public ArrayObjectFormatter(BiFunction<Type, Map<String, Object>, String> typeStringFunction) {
        super(typeStringFunction);
    }

    /**
     * Generates a prefix for the array object string representation,
     * including the array's type and length.
     *
     * @param value The object reference for the array.
     * @param options Additional options to format the string.
     * @return A string prefix for the array representation.
     */
    @Override
    protected String getPrefix(ObjectReference value, Map<String, Object> options) {
        String arrayTypeWithLength = String.format("[%s]",
                NumericFormatter.formatNumber(arrayLength(value), options));
        return super.getPrefix(value, options).replaceFirst("\\[]", arrayTypeWithLength);
    }

    /**
     * Determines if this formatter can handle the provided type,
     * specifically checking for array types.
     *
     * @param type The JDI type of the object.
     * @param options Additional options that might influence the formatting.
     * @return True if the type is an array, false otherwise.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return type != null && type.signature().charAt(0) == ARRAY;
    }

    /**
     * Calculates the length of the array.
     *
     * @param value The JDI value representing the array.
     * @return The length of the array.
     */
    private static int arrayLength(Value value) {
        return ((ArrayReference) value).length();
    }
}
