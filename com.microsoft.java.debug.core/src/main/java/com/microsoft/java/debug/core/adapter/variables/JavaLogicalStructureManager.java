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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.Expression;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.ExpressionType;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.LogicalVariable;
import com.sun.jdi.ObjectReference;

public class JavaLogicalStructureManager {
    private static final List<JavaLogicalStructure> supportedLogicalStructures = Collections.synchronizedList(new ArrayList<>());

    static {
        supportedLogicalStructures.add(new JavaLogicalStructure("java.util.Map",
                new Expression(ExpressionType.METHOD, "entrySet"),
                new Expression(ExpressionType.METHOD, "size"),
                new LogicalVariable[0]));
        supportedLogicalStructures.add(new JavaLogicalStructure("java.util.Map$Entry", null, null, new LogicalVariable[] {
            new LogicalVariable("key", new Expression(ExpressionType.METHOD, "getKey")),
            new LogicalVariable("value", new Expression(ExpressionType.METHOD, "getValue"))
        }));
        supportedLogicalStructures.add(new JavaLogicalStructure("java.util.Collection",
                new Expression(ExpressionType.METHOD, "toArray"),
                new Expression(ExpressionType.METHOD, "size"),
                new LogicalVariable[0]));
    }

    /**
     * Return the provided logical structure handler for the given variable.
     */
    public static JavaLogicalStructure getLogicalStructure(ObjectReference obj) {
        for (JavaLogicalStructure structure : supportedLogicalStructures) {
            if (structure.providesLogicalStructure(obj)) {
                return structure;
            }
        }

        return null;
    }

    public static boolean isIndexedVariable(ObjectReference obj) {
        JavaLogicalStructure structure = getLogicalStructure(obj);
        return structure != null && structure.isIndexedVariable();
    }

    public static Expression getLogicalSize(ObjectReference obj) {
        JavaLogicalStructure structure = getLogicalStructure(obj);
        return structure == null ? null : structure.getSize();
    }
}
