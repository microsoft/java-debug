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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.LogicalStructureExpression;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.LogicalStructureExpressionType;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.LogicalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;

public class JavaLogicalStructureManager {
    private static final List<JavaLogicalStructure> supportedLogicalStructures = Collections.synchronizedList(new ArrayList<>());

    static {
        supportedLogicalStructures.add(new JavaLogicalStructure("java.util.Map",
            new LogicalStructureExpression(LogicalStructureExpressionType.METHOD, new String[] {"entrySet", "()Ljava/util/Set;"}, "entrySet()"),
            new LogicalStructureExpression(LogicalStructureExpressionType.METHOD, new String[] {"size", "()I"}),
            new LogicalVariable[0]
        ));
        supportedLogicalStructures.add(new JavaLogicalStructure("java.util.Map$Entry", "java.util.Map.Entry", null, null,
            new LogicalVariable[] {
                new LogicalVariable("key",
                    new LogicalStructureExpression(LogicalStructureExpressionType.METHOD, new String[] {"getKey", "()Ljava/lang/Object;"}, "getKey()", true)
                ),
                new LogicalVariable("value",
                    new LogicalStructureExpression(LogicalStructureExpressionType.METHOD,
                        new String[] {"getValue", "()Ljava/lang/Object;"}, "getValue()", true)
                )}
        ));
        supportedLogicalStructures.add(new JavaLogicalStructure("java.util.List",
            new LogicalStructureExpression(LogicalStructureExpressionType.METHOD, new String[] {"toArray", "()[Ljava/lang/Object;"}, "get(%s)", true),
            new LogicalStructureExpression(LogicalStructureExpressionType.METHOD, new String[] {"size", "()I"}),
            new LogicalVariable[0]
        ));
        supportedLogicalStructures.add(new JavaLogicalStructure("java.util.Collection",
            new LogicalStructureExpression(LogicalStructureExpressionType.METHOD, new String[] {"toArray", "()[Ljava/lang/Object;"}, "toArray()", true),
            new LogicalStructureExpression(LogicalStructureExpressionType.METHOD, new String[] {"size", "()I"}),
            new LogicalVariable[0]
        ));
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

    /**
     * Return true if the specified Object has defined the logical size.
     */
    public static boolean isIndexedVariable(ObjectReference obj) {
        JavaLogicalStructure structure = getLogicalStructure(obj);
        return structure != null && structure.getSizeExpression() != null;
    }

    /**
     * Return the logical size if the specified Object has defined the logical size.
     */
    public static Value getLogicalSize(ObjectReference thisObject, ThreadReference thread, IEvaluationProvider evaluationEngine)
            throws CancellationException, InterruptedException, IllegalArgumentException, ExecutionException, UnsupportedOperationException {
        JavaLogicalStructure structure = getLogicalStructure(thisObject);
        if (structure == null) {
            return null;
        }

        return structure.getSize(thisObject, thread, evaluationEngine);
    }
}
