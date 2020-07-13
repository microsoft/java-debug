/*******************************************************************************
 * Copyright (c) 2019-2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter.variables;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public class JavaLogicalStructure {
    // The binary type name. For inner type, the binary name uses '$' as the separator, e.g. java.util.Map$Entry.
    private final String type;
    // The fully qualified name, which uses '.' as the separator, e.g. java.util.Map.Entry.
    private final String fullyQualifiedName;
    private final LogicalStructureExpression valueExpression;
    private final LogicalStructureExpression sizeExpression;
    private final LogicalVariable[] variables;

    /**
     * Constructor.
     */
    public JavaLogicalStructure(String type, LogicalStructureExpression valueExpression, LogicalStructureExpression sizeExpression,
            LogicalVariable[] variables) {
        this(type, type, valueExpression, sizeExpression, variables);
    }

    /**
     * Constructor.
     */
    public JavaLogicalStructure(String type, String fullyQualifiedName, LogicalStructureExpression valueExpression, LogicalStructureExpression sizeExpression,
            LogicalVariable[] variables) {
        this.valueExpression = valueExpression;
        this.type = type;
        this.fullyQualifiedName = fullyQualifiedName;
        this.sizeExpression = sizeExpression;
        this.variables = variables;
    }

    public String getType() {
        return type;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
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
            throws CancellationException, InterruptedException, IllegalArgumentException, ExecutionException, UnsupportedOperationException {
        if (sizeExpression  == null) {
            throw new UnsupportedOperationException("The object hasn't defined the logical size operation.");
        }

        return getValue(thisObject, sizeExpression, thread, evaluationEngine);
    }

    /**
     * Return the logical value of the specified thisObject.
     */
    public Value getValue(ObjectReference thisObject, ThreadReference thread, IEvaluationProvider evaluationEngine)
            throws CancellationException, IllegalArgumentException, InterruptedException, ExecutionException {
        return getValue(thisObject, valueExpression, thread, evaluationEngine);
    }

    private static Value getValue(ObjectReference thisObject, LogicalStructureExpression expression, ThreadReference thread,
            IEvaluationProvider evaluationEngine) throws CancellationException, IllegalArgumentException, InterruptedException, ExecutionException {
        if (expression.type == LogicalStructureExpressionType.METHOD) {
            if (expression.value == null || expression.value.length < 2) {
                throw new IllegalArgumentException("The method expression should contain at least methodName and methodSignature!");
            }
            return evaluationEngine.invokeMethod(thisObject, expression.value[0], expression.value[1], null, thread, false).get();
        } else if (expression.type == LogicalStructureExpressionType.FIELD) {
            if (expression.value == null || expression.value.length < 1) {
                throw new IllegalArgumentException("The field expression should contain the field name!");
            }
            return getValueByField(thisObject, expression.value[0], thread);
        } else {
            if (expression.value == null || expression.value.length < 1) {
                throw new IllegalArgumentException("The evaluation expression should contain a valid expression statement!");
            }
            return evaluationEngine.evaluate(expression.value[0], thisObject, thread).get();
        }
    }

    private static Value getValueByField(ObjectReference thisObject, String fieldName, ThreadReference thread) {
        Field targetField = thisObject.referenceType().fieldByName(fieldName);
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
                throws CancellationException, IllegalArgumentException, InterruptedException, ExecutionException {
            return JavaLogicalStructure.getValue(thisObject, valueExpression, thread, evaluationEngine);
        }

        public String getEvaluateName() {
            if (valueExpression == null || valueExpression.evaluateName == null) {
                return name;
            }

            return valueExpression.evaluateName;
        }

        public boolean returnUnboundedType() {
            return valueExpression != null && valueExpression.returnUnboundedType;
        }
    }

    public static class LogicalStructureExpression {
        public LogicalStructureExpressionType type;
        public String[] value;
        public String evaluateName;
        public boolean returnUnboundedType = false;

        /**
         *  Constructor.
         */
        public LogicalStructureExpression(LogicalStructureExpressionType type, String[] value) {
            this(type, value, null);
        }

        /**
         *  Constructor.
         */
        public LogicalStructureExpression(LogicalStructureExpressionType type, String[] value, String evaluateName) {
            this.type = type;
            this.value = value;
            this.evaluateName = evaluateName;
        }

        /**
         *  Constructor.
         */
        public LogicalStructureExpression(LogicalStructureExpressionType type, String[] value, String evaluateName, boolean returnUnboundedType) {
            this.type = type;
            this.value = value;
            this.evaluateName = evaluateName;
            this.returnUnboundedType = returnUnboundedType;
        }
    }

    public static enum LogicalStructureExpressionType {
        FIELD, METHOD, EVALUATION_SNIPPET
    }
}
