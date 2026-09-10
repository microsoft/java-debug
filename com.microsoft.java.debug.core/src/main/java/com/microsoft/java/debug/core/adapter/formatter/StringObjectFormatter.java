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

package com.microsoft.java.debug.core.adapter.formatter;

import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.STRING;
import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.STRING_SIGNATURE;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.sun.jdi.StringReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

/**
 * A formatter dedicated to string objects, providing enhanced string representations
 * with support for maximum length constraints.
 */
public class StringObjectFormatter extends ObjectFormatter implements IValueFormatter {
    public static final String MAX_STRING_LENGTH_OPTION = "max_string_length";
    private static final int DEFAULT_MAX_STRING_LENGTH = 0;
    private static final String QUOTE_STRING = "\"";

    public StringObjectFormatter() {
        super(null);
    }

    /**
     * Provides the default options for string formatting, including a maximum string length.
     *
     * @return A map containing default options for string formatting.
     */
    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put(MAX_STRING_LENGTH_OPTION, DEFAULT_MAX_STRING_LENGTH);
        return options;
    }

    /**
     * Formats a `StringReference` into a string representation, applying maximum length constraints if specified.
     *
     * @param value The string reference to format.
     * @param options A map containing formatting options such as 'max_string_length'.
     * @return A formatted string representation enclosed in quotes.
     */
    @Override
    public String toString(Object value, Map<String, Object> options) {
        int maxLength = getMaxStringLength(options);
        return String.format("\"%s\"",
                maxLength > 0 ? StringUtils.abbreviate(((StringReference) value).value(), maxLength) : ((StringReference) value).value());
    }

    /**
     * Determines if this formatter can handle the provided type, specifically targeting string types.
     *
     * @param type The JDI type of the object.
     * @param options Unused in this method.
     * @return True if the type is a string, false otherwise.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return type != null && (type.signature().charAt(0) == STRING
                || type.signature().equals(STRING_SIGNATURE));
    }

    /**
     * Converts a string value back into a `StringReference`, considering any provided options.
     *
     * @param value The string value to convert.
     * @param type The JDI type for the conversion, expected to be `StringType`.
     * @param options Unused in this formatter.
     * @return A `StringReference` representing the given string value.
     */
    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        if (value == null || NullObjectFormatter.NULL_STRING.equals(value)) {
            return null;
        }
        if (value.length() >= 2
                && value.startsWith(QUOTE_STRING)
                && value.endsWith(QUOTE_STRING)) {
            return type.virtualMachine().mirrorOf(StringUtils.substring(value, 1, -1));
        }
        return type.virtualMachine().mirrorOf(value);
    }

    /**
     * Retrieves the maximum string length from the provided options, applying a default if not specified.
     *
     * @param options Formatting options potentially containing a 'max_string_length' key.
     * @return The maximum string length to apply, or a default value if unspecified.
     */
    private static int getMaxStringLength(Map<String, Object> options) {
        return options.containsKey(MAX_STRING_LENGTH_OPTION)
                ? (int) options.get(MAX_STRING_LENGTH_OPTION) : DEFAULT_MAX_STRING_LENGTH;
    }
}
