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

import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.CHAR;

import java.util.Map;

import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

/**
 * Formatter for character values in the Java Debug Interface (JDI).
 * Implements the IValueFormatter interface to provide string representations
 * and value conversions for char types.
 */
public class CharacterFormatter implements IValueFormatter {

    /**
     * Converts a JDI value object into its string representation.
     *
     * @param value The character value to be formatted to string.
     * @param options Additional options affecting the format; unused in this formatter.
     * @return A string representation of the character value, or "null" if the value is null.
     */
    @Override
    public String toString(Object value, Map<String, Object> options) {
        return value == null ? NullObjectFormatter.NULL_STRING : value.toString();
    }

    /**
     * Determines if this formatter is applicable for the given type,
     * specifically checking for char types.
     *
     * @param type The JDI type of the object.
     * @param options Additional options that might influence the formatting; unused in this formatter.
     * @return True if the type is a char, false otherwise.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        if (type == null) {
            return false;
        }
        char signature0 = type.signature().charAt(0);
        return signature0 == CHAR;
    }

    /**
     * Converts a string representation of a character into a JDI Value object.
     *
     * @param value The string value to convert, expected to be a single character or a character within single quotes.
     * @param type The expected JDI type; used to fetch the VirtualMachine reference.
     * @param options Additional conversion options; unused in this formatter.
     * @return A JDI Value representing the character indicated by the input string. Handles both single characters and characters encapsulated in single quotes.
     */
    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        VirtualMachine vm = type.virtualMachine();
        if (value == null) {
            return null;
        }
        // Supports parsing character values encapsulated in single quotes, e.g., 'a'.
        if (value.length() == 3
                && value.startsWith("'")
                && value.endsWith("'")) {
            return type.virtualMachine().mirrorOf(value.charAt(1));
        }
        // Default case for single characters not encapsulated in quotes.
        return vm.mirrorOf(value.charAt(0));
    }
}