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

/**
 * Defines the numeric formats that can be used for displaying numbers during debugging.
 * This enumeration is utilized by formatters to convert numeric values into their
 * string representations in various numeric bases.
 */
public enum NumericFormatEnum {
    /**
     * Represents hexadecimal format.
     * Numbers displayed in this format are prefixed with '0x' to indicate hexadecimal.
     */
    HEX,

    /**
     * Represents octal format.
     * Numbers displayed in this format are prefixed with '0' to indicate octal.
     */
    OCT,

    /**
     * Represents decimal format.
     * This is the standard numeric format, displaying numbers in base 10 with no prefix.
     */
    DEC
}
