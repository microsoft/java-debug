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

public class NumericFormatter implements IValueFormatter {
    public static final String NUMERIC_FORMAT_OPTION = "numeric_format";
    public static final String NUMERIC_PRECISION_OPTION = "numeric_precision";
    private static final NumericFormatEnum DEFAULT_NUMERIC_FORMAT = NumericFormatEnum.DEC;
    private static final int DEFAULT_NUMERIC_PRECISION = 0;

    private static final String HEX_PREFIX = "0x";
    private static final String OCT_PREFIX = "0";
    private static final String BIN_PREFIX = "0b";

    /**
     * Get the string representations for an object.
     *
     * @param obj     the value object
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

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put(NUMERIC_FORMAT_OPTION, DEFAULT_NUMERIC_FORMAT);
        options.put(NUMERIC_PRECISION_OPTION, DEFAULT_NUMERIC_PRECISION);
        return options;
    }

    static String formatNumber(long value, Map<String, Object> options) {
        NumericFormatEnum formatEnum = getNumericFormatOption(options);
        switch (formatEnum) {
            case HEX:
                return HEX_PREFIX + Long.toHexString(value);
            case OCT:
                return OCT_PREFIX + Long.toOctalString(value);
            case BIN:
                return BIN_PREFIX + Long.toBinaryString(value);
            default:
                return Long.toString(value);
        }
    }

    private static long parseNumber(String number) {
        return number.startsWith(BIN_PREFIX)
            ? Long.parseLong(number.substring(2), 2) : Long.decode(number);
    }

    private static double parseFloatDouble(String number) {
        return Double.parseDouble(number);
    }

    private static String formatFloatDouble(double value, Map<String, Object> options) {
        int precision = getFractionPrecision(options);
        return String.format(precision > 0 ? String.format("%%.%df", precision) : "%f", value);
    }

    private static NumericFormatEnum getNumericFormatOption(Map<String, Object> options) {
        return options.containsKey(NUMERIC_FORMAT_OPTION)
                ? (NumericFormatEnum) options.get(NUMERIC_FORMAT_OPTION) : DEFAULT_NUMERIC_FORMAT;
    }

    private static boolean hasFraction(char signature0) {
        return signature0 == FLOAT
                || signature0 == DOUBLE;
    }

    private static int getFractionPrecision(Map<String, Object> options) {
        return options.containsKey(NUMERIC_PRECISION_OPTION)
                ? (int) options.get(NUMERIC_PRECISION_OPTION) : DEFAULT_NUMERIC_PRECISION;
    }
}
