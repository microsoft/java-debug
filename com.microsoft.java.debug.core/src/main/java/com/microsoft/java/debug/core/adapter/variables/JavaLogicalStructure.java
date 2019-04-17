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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public class JavaLogicalStructure {
    private final String type;
    private final LogicalStructureExpression valueExpression;
    private final LogicalStructureExpression sizeExpression;
    private final LogicalVariable[] variables;

    /**
     * Constructor.
     */
    public JavaLogicalStructure(String type, LogicalStructureExpression valueExpression, LogicalStructureExpression sizeExpression,
            LogicalVariable[] variables) {
        this.valueExpression = valueExpression;
        this.type = type;
        this.sizeExpression = sizeExpression;
        this.variables = variables;
    }

    public String getType() {
        return type;
    }

    public LogicalStructureExpression getValueExpression() {
        return valueExpression;
    }

    public LogicalStructureExpression getSizeExpression() {
        return sizeExpression;
    }

    public LogicalVariable[] getVariables() {
        return variables;
    }

    /**
     * Returns whether to support the logical structure view for the given object instance.
     */
    public boolean providesLogicalStructure(ObjectReference obj) {
        Type variableType = obj.type();
        if (!(variableType instanceof ClassType)) {
            return false;
        }

        ClassType classType = (ClassType) variableType;
        while (classType != null) {
            if (Objects.equals(type, classType.name())) {
                return true;
            }

            classType = classType.superclass();
        }

        List<InterfaceType> interfaceTypes = ((ClassType) variableType).allInterfaces();
        for (InterfaceType interfaceType : interfaceTypes) {
            if (Objects.equals(type, interfaceType.name())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return the logical size of the specified thisObject.
     */
    public Value getSize(ObjectReference thisObject, ThreadReference thread, IEvaluationProvider evaluationEngine)
            throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException,
            InterruptedException, ExecutionException, UnsupportedOperationException {
        if (sizeExpression  == null) {
            throw new UnsupportedOperationException("The object hasn't defined the logical size operation.");
        }

        return getValue(thisObject, sizeExpression, thread, evaluationEngine);
    }

    /**
     * Return the logical value of the specified thisObject.
     */
    public Value getValue(ObjectReference thisObject, ThreadReference thread, IEvaluationProvider evaluationEngine)
            throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException,
            InterruptedException, ExecutionException {
        return getValue(thisObject, valueExpression, thread, evaluationEngine);
    }

    private static Value getValue(ObjectReference thisObject, LogicalStructureExpression expression, ThreadReference thread,
            IEvaluationProvider evaluationEngine)
            throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException,
            InterruptedException, ExecutionException {
        if (expression.type == LogicalStructureExpressionType.METHOD) {
            return getValueByMethod(thisObject, expression.value, thread);
        } else if (expression.type == LogicalStructureExpressionType.FIELD) {
            return getValueByField(thisObject, expression.value, thread);
        } else {
            return evaluationEngine.evaluate(expression.value, thisObject, thread).get();
        }
    }

    private static Value getValueByMethod(ObjectReference thisObject, String methodName, ThreadReference thread)
            throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {
        List<Method> methods = thisObject.referenceType().allMethods();
        Method targetMethod = null;
        for (Method method : methods) {
            if (Objects.equals(method.name(), methodName) && method.argumentTypeNames().isEmpty()) {
                targetMethod = method;
                break;
            }
        }

        if (targetMethod == null) {
            return null;
        }

        return thisObject.invokeMethod(thread, targetMethod, Collections.EMPTY_LIST, ObjectReference.INVOKE_SINGLE_THREADED);
    }

    private static Value getValueByField(ObjectReference thisObject, String filedName, ThreadReference thread) {
        List<Field> fields = thisObject.referenceType().allFields();
        Field targetField = null;
        for (Field field : fields) {
            if (Objects.equals(field.name(), filedName)) {
                targetField = field;
                break;
            }
        }

        if (targetField == null) {
            return null;
        }

        return thisObject.getValue(targetField);
    }

    public static class LogicalVariable {
        private final String name;
        private final LogicalStructureExpression valueExpression;

        public LogicalVariable(String name, LogicalStructureExpression valueExpression) {
            this.name = name;
            this.valueExpression = valueExpression;
        }

        public String getName() {
            return name;
        }

        public Value getValue(ObjectReference thisObject, ThreadReference thread, IEvaluationProvider evaluationEngine)
                throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException,
                InterruptedException, ExecutionException {
            return JavaLogicalStructure.getValue(thisObject, valueExpression, thread, evaluationEngine);
        }
    }

    public static class LogicalStructureExpression {
        public LogicalStructureExpressionType type;
        public String value;

        /**
         *  Constructor.
         */
        public LogicalStructureExpression(LogicalStructureExpressionType type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    public static enum LogicalStructureExpressionType {
        FIELD, METHOD, EVALUATION_SNIPPET
    }
}
