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

import java.util.HashMap;
import java.util.Map;

import com.sun.jdi.Type;

/**
 * Implements a type formatter to provide string representations of JDI types,
 * with the option to use fully qualified class names or simple names.
 * It facilitates readability and customization in how types are presented during debugging.
 */
public class SimpleTypeFormatter implements ITypeFormatter {
    public static final String QUALIFIED_CLASS_NAME_OPTION = "qualified_class_name";
    private static final boolean DEFAULT_QUALIFIED_CLASS_NAME_OPTION = false;

    /**
     * Formats the given JDI type into a string representation.
     * The format can be controlled via options to either show the fully qualified class name or just the simple name.
     *
     * @param type The JDI type to be formatted.
     * @param options Formatting options that dictate whether to use the fully qualified name.
     * @return The formatted string representation of the type.
     */
    @Override
    public String toString(Object type, Map<String, Object> options) {
        if (type == null) {
            return NullObjectFormatter.NULL_STRING;
        }

        String typeName = ((Type) type).name();
        return showQualifiedClassName(options) ? typeName : trimTypeName(typeName);
    }

    /**
     * Always accepts the provided type since it is capable of formatting any type.
     *
     * @param type The JDI type.
     * @param options Not used in this method.
     * @return Always true, indicating that any type is supported.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return true;
    }

    /**
     * Provides the default formatting options for this formatter.
     *
     * @return A map containing the default options, specifying the use of simple names by default.
     */
    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put(QUALIFIED_CLASS_NAME_OPTION, DEFAULT_QUALIFIED_CLASS_NAME_OPTION);
        return options;
    }

    /**
     * Trims a fully qualified class name to its simple name.
     * If the class name contains a dot, it returns the substring after the last dot.
     *
     * @param type The fully qualified class name.
     * @return The simple class name without package qualification.
     */
    public static String trimTypeName(String type) {
        if (type.indexOf('.') >= 0) {
            type = type.substring(type.lastIndexOf('.') + 1);
        }
        return type;
    }

    /**
     * Determines whether to show fully qualified class names based on the provided options.
     *
     * @param options The formatting options map which may contain a value for {@link #QUALIFIED_CLASS_NAME_OPTION}.
     * @return True if the options specify to show fully qualified names; otherwise, false.
     */
    private static boolean showQualifiedClassName(Map<String, Object> options) {
        return options.containsKey(QUALIFIED_CLASS_NAME_OPTION)
                ? (Boolean) options.get(QUALIFIED_CLASS_NAME_OPTION) : DEFAULT_QUALIFIED_CLASS_NAME_OPTION;
    }
}
