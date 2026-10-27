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

import java.util.Map;

import com.sun.jdi.Type;
import com.sun.jdi.Value;

/**
 * A formatter dedicated to handling null values in the Java Debug Interface (JDI).
 * Implements the {@link IValueFormatter} interface, providing a uniform representation
 * for null objects during debugging sessions.
 */
public class NullObjectFormatter implements IValueFormatter {
    public static final String NULL_STRING = "null";

    /**
     * Provides a string representation for null values.
     *
     * @param value The value to be formatted, expected to be null in this context.
     * @param options Additional options affecting the format; unused in this formatter.
     * @return A constant string "null" to represent null values.
     */
    @Override
    public String toString(Object value, Map<String, Object> options) {
        return NULL_STRING;
    }

    /**
     * Determines if this formatter is applicable for the given type,
     * specifically targeting null types.
     *
     * @param type The JDI type of the object, expected to be null.
     * @param options Additional options that might influence the decision; unused in this formatter.
     * @return True if the type is null, indicating this formatter should be used.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return type == null;
    }

    /**
     * Converts a string representation of null into a JDI Value object, which is also null.
     * Throws {@link UnsupportedOperationException} if a non-null value is attempted to be set.
     *
     * @param value The string value to convert, expected to match "null".
     * @param type The expected JDI type; unused in this formatter as the output is always null.
     * @param options Additional conversion options; unused in this formatter.
     * @return null, as the only supported conversion is from the string "null" to a null {@link Value}.
     * @throws UnsupportedOperationException if set value is attempted with a non-null value.
     */
    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        if (value == null || NULL_STRING.equals(value)) {
            return null;
        }
        throw new UnsupportedOperationException("Set value is not supported by NullObjectFormatter.");
    }

}
