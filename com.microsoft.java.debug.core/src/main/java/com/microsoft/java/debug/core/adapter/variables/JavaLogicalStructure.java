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

import org.apache.commons.lang3.StringUtils;

import com.sun.jdi.ClassType;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;

public class JavaLogicalStructure {
    private final String type;
    private final String value;
    private final String size;
    private final LogicalVariable[] variables;

    /**
     * Constructor.
     */
    public JavaLogicalStructure(String type, String value, String size, LogicalVariable[] variables) {
        this.value = value;
        this.type = type;
        this.size = size;
        this.variables = variables;
    }

    public String getValue() {
        return value;
    }

    public String getType() {
        return type;
    }

    public String getSize() {
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
        return StringUtils.isNotBlank(size);
    }

    public static class LogicalVariable {
        private final String name;
        private final String value;

        public LogicalVariable(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}
