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

import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.*;

import java.util.Map;
import java.util.function.BiFunction;

import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

/**
 * Provides a generic formatter for objects within the Java Debug Interface (JDI),
 * capable of handling a broad range of object types including basic objects, arrays,
 * strings, threads, thread groups, class loaders, and class objects.
 */
public class ObjectFormatter implements IValueFormatter {

    /**
     * A function that defines how types are converted to their string representations.
     */
    protected final BiFunction<Type, Map<String, Object>, String> typeToStringFunction;

    /**
     * Constructs an ObjectFormatter with a custom type-to-string function.
     *
     * @param typeToStringFunction A function that generates string representations based on the JDI type and formatting options.
     */
    public ObjectFormatter(BiFunction<Type, Map<String, Object>, String> typeToStringFunction) {
        this.typeToStringFunction = typeToStringFunction;
    }

    /**
     * Formats an object reference to its string representation, including a type prefix and a unique identifier postfix.
     *
     * @param obj The object to format.
     * @param options Additional formatting options.
     * @return The formatted string representation of the object.
     */
    @Override
    public String toString(Object obj, Map<String, Object> options) {
        return String.format("%s@%s", getPrefix((ObjectReference) obj, options),
                getIdPostfix((ObjectReference) obj, options));
    }

    /**
     * Determines if this formatter is applicable for the provided JDI type.
     *
     * @param type The type of the object to check.
     * @param options Additional options that might influence formatting (unused here).
     * @return True if the formatter supports the given type; false otherwise.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        if (type == null) {
            return false;
        }
        char tag = type.signature().charAt(0);
        return (tag == OBJECT) || (tag == ARRAY) || (tag == STRING)
                || (tag == THREAD) || (tag == THREAD_GROUP)
                || (tag == CLASS_LOADER) || (tag == CLASS_OBJECT);
    }

    /**
     * Unsupported operation for setting the value of an object from its string representation.
     *
     * @param value The string representation of the value to set.
     * @param type The JDI type of the object.
     * @param options Formatting options (unused).
     * @return Currently, always throws UnsupportedOperationException.
     */
    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        if (value == null || NullObjectFormatter.NULL_STRING.equals(value)) {
            return null;
        }
        throw new UnsupportedOperationException(String.format("Set value is not supported yet for type %s.", type.name()));
    }

    /**
     * Generates a type-specific prefix for the object's string representation.
     *
     * @param value The object whose type prefix is to be generated.
     * @param options Additional formatting options.
     * @return A string representing the type-specific prefix.
     */
    protected String getPrefix(ObjectReference value, Map<String, Object> options) {
        return typeToStringFunction.apply(value.type(), options);
    }

    /**
     * Generates a unique identifier postfix for the object's string representation.
     *
     * @param obj The object whose unique ID postfix is to be generated.
     * @param options Additional formatting options.
     * @return A string representing the object's unique identifier.
     */
    protected static String getIdPostfix(ObjectReference obj, Map<String, Object> options) {
        return NumericFormatter.formatNumber(obj.uniqueID(), options);
    }
}
