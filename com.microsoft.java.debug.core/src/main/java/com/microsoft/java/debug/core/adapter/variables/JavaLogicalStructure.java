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

import java.util.List;
import java.util.Objects;

import com.sun.jdi.ClassType;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;

public class JavaLogicalStructure {
    private final String type;
    private final LogicalStructureExpression value;
    private final LogicalStructureExpression size;
    private final LogicalVariable[] variables;

    /**
     * Constructor.
     */
    public JavaLogicalStructure(String type, LogicalStructureExpression value, LogicalStructureExpression size, LogicalVariable[] variables) {
        this.value = value;
        this.type = type;
        this.size = size;
        this.variables = variables;
    }

    public LogicalStructureExpression getValue() {
        return value;
    }

    public String getType() {
        return type;
    }

    public LogicalStructureExpression getSize() {
        return size;
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

    public boolean isIndexedVariable() {
        return size != null;
    }

    public static class LogicalVariable {
        private final String name;
        private final LogicalStructureExpression value;

        public LogicalVariable(String name, LogicalStructureExpression value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public LogicalStructureExpression getValue() {
            return value;
        }
    }

    public static class LogicalStructureExpression {
        public LogicalStructureExpressionType type;
        public String value;

        /**
         *  Constructor.
         */
        public LogicalStructureExpression(LogicalStructureExpressionType type, String value) {
            super();
            this.type = type;
            this.value = value;
        }
    }

    public static enum LogicalStructureExpressionType {
        FIELD, METHOD, EVALUATION_SNIPPET
    }
}
