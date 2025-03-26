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

import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.CLASS_OBJECT;
import static com.microsoft.java.debug.core.adapter.formatter.TypeIdentifiers.CLASS_SIGNATURE;

import java.util.Map;
import java.util.function.BiFunction;

import com.sun.jdi.ClassObjectReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;

/**
 * Formatter for Java ClassObjectReferences in the Java Debug Interface (JDI).
 * Extends the ObjectFormatter to provide customized string representations of Class objects,
 * incorporating additional class type information.
 */
public class ClassObjectFormatter extends ObjectFormatter {

    /**
     * Constructs a ClassObjectFormatter with a specific function to generate string representations based on the JDI type.
     *
     * @param typeStringFunction A function that generates a string representation based on the JDI type and formatting options.
     */
    public ClassObjectFormatter(BiFunction<Type, Map<String, Object>, String> typeStringFunction) {
        super(typeStringFunction);
    }

    /**
     * Generates a string prefix for ClassObjectReference instances, enhancing the default object prefix with class type information.
     *
     * @param value The object reference, expected to be a ClassObjectReference.
     * @param options Additional formatting options that may influence the output.
     * @return A string prefix that includes both the default object prefix and the class's type information.
     */
    @Override
    protected String getPrefix(ObjectReference value, Map<String, Object> options) {
        Type classType = ((ClassObjectReference) value).reflectedType();
        return String.format("%s (%s)", super.getPrefix(value, options),
                typeToStringFunction.apply(classType, options));
    }

    /**
     * Determines if this formatter can handle the provided type, specifically targeting ClassObject and its signature.
     *
     * @param type The JDI type of the object.
     * @param options Additional options that might influence the decision; unused in this formatter.
     * @return True if the type is a ClassObject or matches the Class signature, false otherwise.
     */
    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return super.acceptType(type, options) && (type.signature().charAt(0) == CLASS_OBJECT
                || type.signature().equals(CLASS_SIGNATURE));
    }
}
