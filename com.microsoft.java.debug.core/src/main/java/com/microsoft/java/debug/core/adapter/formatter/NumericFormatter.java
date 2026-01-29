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

import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.BYTE;
import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.DOUBLE;
import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.FLOAT;
import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.INT;
import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.LONG;
import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.SHORT;

import java.util.HashMap;
import java.util.Map;

import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

/**
 * Implements a formatter for numeric values, providing string representations
 * in various formats (decimal, hexadecimal, octal) and precision levels.
 * This class supports long, int, short, byte, float, and double types from the JDI.
 */
public class NumericFormatter implements IValueFormatter {
    public static final String NUMERIC_FORMAT_OPTION = "numeric_format";
    public static final String NUMERIC_PRECISION_OPTION = "numeric_precision";
    private static final NumericFormatEnum DEFAULT_NUMERIC_FORMAT = NumericFormatEnum.DEC;
    private static final int DEFAULT_NUMERIC_PRECISION = 0;
    private static final Map<NumericFormatEnum, String> enumFormatMap = new HashMap<>();

    static {
        enumFormatMap.put(NumericFormatEnum.DEC, "%d");
        enumFormatMap.put(NumericFormatEnum.HEX, "%#x");
        enumFormatMap.put(NumericFormatEnum.OCT, "%#o");
    }

    /**
     * Get the string representations for an object.
     *
     * @param obj the value object
     * @param options extra information for printing
     * @return the string representations.
     */
    @Override
    public String toString(Object obj, Map<String, Object> options) {
        Value value = (Value) obj;
        char signature0 = value.type().signature().charAt(0);
        if (signature0 == LONG
                || signature0 == INT
                || signature0 == SHORT
                || signature0 == BYTE) {
            return formatNumber(Long.parseLong(value.toString()), options);
        } else if (hasFraction(signature0)) {
            return formatFloatDouble(Double.parseDouble(value.toString()), options);
        }

        throw new UnsupportedOperationException(String.format("%s is not a numeric type.", value.type().name()));
    }

    /**
     * Converts a string representation of a number into a JDI Value based on the type's signature.
     * Supports conversion for numeric types including long, int, short, byte, float, and double.
     *
     * @param value The string value to be converted.
     * @param type The JDI type for the conversion.
     * @param options Unused in this formatter.
     * @return A JDI Value representing the numeric value.
     * @throws UnsupportedOperationException if the type is not numeric.
     */
    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        VirtualMachine vm = type.virtualMachine();
        char signature0 = type.signature().charAt(0);
        if (signature0 == LONG
                || signature0 == INT
                || signature0 == SHORT
                || signature0 == BYTE) {
            long number = parseNumber(value);
            if (signature0 == LONG) {
                return vm.mirrorOf(number);
            } else if (signature0 == INT) {
                return vm.mirrorOf((int) number);
            } else if (signature0 == SHORT) {
                return vm.mirrorOf((short) number);
            } else if (signature0 == BYTE) {
                return vm.mirrorOf((byte) number);
            }
        } else if (hasFraction(signature0)) {
            double doubleNumber = parseFloatDouble(value);
            if (signature0 == DOUBLE) {
                return vm.mirrorOf(doubleNumber);
            } else {
                return vm.mirrorOf((float) doubleNumber);
            }
        }

        throw new UnsupportedOperationException(String.format("%s is not a numeric type.", type.name()));
    }


    /**
     * The conditional function for this formatter.
     *
     * @param type the JDI type
     * @return whether or not this formatter is expected to work on this value.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        if (type == null) {
            return false;
        }
        char signature0 = type.signature().charAt(0);
        return signature0 == LONG
                || signature0 == INT
                || signature0 == SHORT
                || signature0 == BYTE
                || signature0 == FLOAT
                || signature0 == DOUBLE;
    }

    /**
     * Provides the default formatting options for numeric values.
     *
     * @return A map containing default options for numeric format and precision.
     */
    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put(NUMERIC_FORMAT_OPTION, DEFAULT_NUMERIC_FORMAT);
        options.put(NUMERIC_PRECISION_OPTION, DEFAULT_NUMERIC_PRECISION);
        return options;
    }

    /**
     * Formats a numeric value according to the specified numeric format in the options.
     *
     * @param value The numeric value to format.
     * @param options A map containing the formatting options, which may specify the numeric format.
     * @return A formatted string representation of the numeric value.
     */
    static String formatNumber(long value, Map<String, Object> options) {
        NumericFormatEnum formatEnum = getNumericFormatOption(options);
        return String.format(enumFormatMap.get(formatEnum), value);
    }

    /**
     * Parses a numeric string to its long value representation.
     *
     * @param number The string representation of the number.
     * @return The long value of the parsed number.
     */
    private static long parseNumber(String number) {
        return Long.decode(number);
    }

    /**
     * Parses a numeric string to its double value representation.
     *
     * @param number The string representation of the number.
     * @return The double value of the parsed number.
     */
    private static double parseFloatDouble(String number) {
        return Double.parseDouble(number);
    }

    /**
     * Formats a floating-point number according to the specified precision in the options.
     *
     * @param value The floating-point value to format.
     * @param options A map containing the formatting options, which may specify the numeric precision.
     * @return A formatted string representation of the floating-point value.
     */
    private static String formatFloatDouble(double value, Map<String, Object> options) {
        int precision = getFractionPrecision(options);
        return String.format(precision > 0 ? String.format("%%.%df", precision) : "%f", value);
    }

    /**
     * Retrieves the numeric format option from the provided options map.
     *
     * @param options A map containing the formatting options.
     * @return The specified NumericFormatEnum, or the default format if not specified.
     */
    private static NumericFormatEnum getNumericFormatOption(Map<String, Object> options) {
        return options.containsKey(NUMERIC_FORMAT_OPTION)
                ? (NumericFormatEnum) options.get(NUMERIC_FORMAT_OPTION) : DEFAULT_NUMERIC_FORMAT;
    }

    /**
     * Checks if the given type signature corresponds to a floating-point number.
     *
     * @param signature0 The first character of the type signature.
     * @return True if the type is a floating-point number, false otherwise.
     */
    private static boolean hasFraction(char signature0) {
        return signature0 == FLOAT
                || signature0 == DOUBLE;
    }

    /**
     * Retrieves the numeric precision option from the provided options map.
     *
     * @param options A map containing the formatting options.
     * @return The specified numeric precision, or the default precision if not specified.
     */
    private static int getFractionPrecision(Map<String, Object> options) {
        return options.containsKey(NUMERIC_PRECISION_OPTION)
                ? (int) options.get(NUMERIC_PRECISION_OPTION) : DEFAULT_NUMERIC_PRECISION;
    }
}
