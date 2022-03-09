/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter.variables;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.sun.jdi.ClassType;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public class VariableDetailUtils {
    private static final String STRING_TYPE = "java.lang.String";
    private static final String TO_STRING_METHOD = "toString";
    private static final String TO_STRING_METHOD_SIGNATURE = "()Ljava/lang/String;";
    private static final String ENTRY_TYPE = "java.util.Map$Entry";
    private static final String GET_KEY_METHOD = "getKey";
    private static final String GET_KEY_METHOD_SIGNATURE = "()Ljava/lang/Object;";
    private static final String GET_VALUE_METHOD = "getValue";
    private static final String GET_VALUE_METHOD_SIGNATURE = "()Ljava/lang/Object;";
    private static final Set<String> COLLECTION_TYPES = new HashSet(
            Arrays.asList("java.util.Map", "java.util.Collection", "java.util.Map$Entry"));

    /**
     * Returns the details information for the specified variable.
     */
    public static String formatDetailsValue(Value value, ThreadReference thread, IVariableFormatter variableFormatter, Map<String, Object> options,
            IEvaluationProvider evaluationEngine) {
        if (isClassType(value, STRING_TYPE)) {
            // No need to show additional details information.
            return null;
        } else {
            return computeToStringValue(value, thread, variableFormatter, options, evaluationEngine, true);
        }
    }

    private static String computeToStringValue(Value value, ThreadReference thread, IVariableFormatter variableFormatter,
            Map<String, Object> options, IEvaluationProvider evaluationEngine, boolean isFirstLevel) {
        if (!(value instanceof ObjectReference) || evaluationEngine == null) {
            return null;
        }

        String inheritedType = findInheritedType(value, COLLECTION_TYPES);
        if (inheritedType != null) {
            if (Objects.equals(inheritedType, ENTRY_TYPE)) {
                try {
                    Value keyObject = evaluationEngine.invokeMethod((ObjectReference) value, GET_KEY_METHOD, GET_KEY_METHOD_SIGNATURE,
                            null, thread, false).get();
                    Value valueObject = evaluationEngine.invokeMethod((ObjectReference) value, GET_VALUE_METHOD, GET_VALUE_METHOD_SIGNATURE,
                            null, thread, false).get();
                    String toStringValue = computeToStringValue(keyObject, thread, variableFormatter, options, evaluationEngine, false)
                            + ":"
                            + computeToStringValue(valueObject, thread, variableFormatter, options, evaluationEngine, false);
                    if (!isFirstLevel) {
                        toStringValue = "\"" + toStringValue + "\"";
                    }

                    return toStringValue;
                } catch (InterruptedException | ExecutionException e) {
                    // do nothing.
                }
            } else if (!isFirstLevel) {
                return variableFormatter.valueToString(value, options);
            }
        } else if (containsToStringMethod((ObjectReference) value)) {
            try {
                Value toStringValue = evaluationEngine.invokeMethod((ObjectReference) value, TO_STRING_METHOD, TO_STRING_METHOD_SIGNATURE,
                        null, thread, false).get();
                return variableFormatter.valueToString(toStringValue, options);
            } catch (InterruptedException | ExecutionException e) {
                // do nothing.
            }
        }

        return null;
    }

    private static boolean containsToStringMethod(ObjectReference obj) {
        ReferenceType refType = obj.referenceType();
        if (refType instanceof ClassType) {
            Method m = ((ClassType) refType).concreteMethodByName(TO_STRING_METHOD, TO_STRING_METHOD_SIGNATURE);
            if (m != null) {
                if (!Objects.equals("Ljava/lang/Object;", m.declaringType().signature())) {
                    return true;
                }
            }

            for (InterfaceType iface : ((ClassType) refType).allInterfaces()) {
                List<Method> matches = iface.methodsByName(TO_STRING_METHOD, TO_STRING_METHOD_SIGNATURE);
                for (Method ifaceMethod : matches) {
                    if (!ifaceMethod.isAbstract()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static String findInheritedType(Value value, Set<String> typeNames) {
        if (!(value instanceof ObjectReference)) {
            return null;
        }

        Type variableType = ((ObjectReference) value).type();
        if (!(variableType instanceof ClassType)) {
            return null;
        }

        ClassType classType = (ClassType) variableType;
        while (classType != null) {
            if (typeNames.contains(classType.name())) {
                return classType.name();
            }

            classType = classType.superclass();
        }

        List<InterfaceType> interfaceTypes = ((ClassType) variableType).allInterfaces();
        for (InterfaceType interfaceType : interfaceTypes) {
            if (typeNames.contains(interfaceType.name())) {
                return interfaceType.name();
            }
        }

        return null;
    }

    private static boolean isClassType(Value value, String typeName) {
        if (!(value instanceof ObjectReference)) {
            return false;
        }

        return Objects.equals(((ObjectReference) value).type().name(), typeName);
    }

    public static boolean isLazyLoadingSupported(Value value) {
        if (isClassType(value, STRING_TYPE)) {
            return false;
        }
        if (!(value instanceof ObjectReference)) {
            return false;
        }
        String inheritedType = findInheritedType(value, COLLECTION_TYPES);
        if (inheritedType == null && !containsToStringMethod((ObjectReference) value)) {
            return false;
        }
        return true;
    }
}
