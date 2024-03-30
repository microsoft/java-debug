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

import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.BOOLEAN;

import java.util.Map;

import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

/**
 * Formatter for boolean values in the Java Debug Interface (JDI).
 * Implements the IValueFormatter interface to provide string representations
 * and value conversions for boolean types.
 */
public class BooleanFormatter implements IValueFormatter {

    /**
     * Converts a JDI value object into its string representation.
     *
     * @param value The value to be formatted to string.
     * @param options Additional options affecting the format; unused in this formatter.
     * @return A string representation of the boolean value, or "null" if the value is null.
     */
    @Override
    public String toString(Object value, Map<String, Object> options) {
        return value == null ? NullObjectFormatter.NULL_STRING : value.toString();
    }

    /**
     * Determines if this formatter is applicable for the given type,
     * specifically checking for boolean types.
     *
     * @param type The JDI type of the object.
     * @param options Additional options that might influence the formatting; unused in this formatter.
     * @return True if the type is a boolean, false otherwise.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        if (type == null) {
            return false;
        }
        char signature0 = type.signature().charAt(0);
        return signature0 == BOOLEAN;
    }

    /**
     * Converts a string representation of a boolean into a JDI Value object.
     *
     * @param value The string value to convert.
     * @param type The expected JDI type; used to fetch the VirtualMachine reference.
     * @param options Additional conversion options; unused in this formatter.
     * @return A JDI Value representing the boolean state indicated by the input string.
     */
    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        VirtualMachine vm = type.virtualMachine();
        return vm.mirrorOf(Boolean.parseBoolean(value));
    }
}