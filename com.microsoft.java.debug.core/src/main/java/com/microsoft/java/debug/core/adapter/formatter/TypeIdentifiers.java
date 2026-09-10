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
 * Defines constants for JDI type signatures. These identifiers are used to
 * recognize specific types during the debugging process, enabling tailored
 * formatting and processing for various data types.
 */
public final class TypeIdentifiers {
    public static final char ARRAY = '['; // Identifies an array type.
    public static final char BYTE = 'B'; // Identifies a byte type.
    public static final char CHAR = 'C'; // Identifies a char type.
    public static final char OBJECT = 'L'; // Identifies an object type.
    public static final char FLOAT = 'F'; // Identifies a float type.
    public static final char DOUBLE = 'D'; // Identifies a double type.
    public static final char INT = 'I'; // Identifies an int type.
    public static final char LONG = 'J'; // Identifies a long type.
    public static final char SHORT = 'S'; // Identifies a short type.
    public static final char BOOLEAN = 'Z'; // Identifies a boolean type.
    public static final char STRING = 's'; // Identifies for string types.
    public static final char THREAD = 't'; // Identifies for thread types.

    // Specific Java types
    public static final char THREAD_GROUP = 'g'; // A custom identifier for ThreadGroup types.
    public static final char CLASS_LOADER = 'l'; // A custom identifier for ClassLoader types.
    public static final char CLASS_OBJECT = 'c'; // A custom identifier for Class types.

    // Full signatures for certain types
    public static final String STRING_SIGNATURE = "Ljava/lang/String;"; // The full signature for the String class.
    public static final String CLASS_SIGNATURE = "Ljava/lang/Class;"; // The full signature for the Class class.
}
