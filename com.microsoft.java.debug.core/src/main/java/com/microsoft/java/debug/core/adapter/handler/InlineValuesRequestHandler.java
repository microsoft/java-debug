/*******************************************************************************
* Copyright (c) 2021 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IStackFrameManager;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructureManager;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.adapter.variables.Variable;
import com.microsoft.java.debug.core.adapter.variables.VariableDetailUtils;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.InlineVariable;
import com.microsoft.java.debug.core.protocol.Requests.InlineValuesArguments;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Value;

import org.apache.commons.lang3.math.NumberUtils;

public class InlineValuesRequestHandler implements IDebugRequestHandler {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.INLINEVALUES);
    }

    /**
     * This request only resolves the values for those non-local variables, such as
     * field variables and captured variables from outer scope. Because the values
     * of local variables in current stackframe are usually expanded by Variables View
     * by default, inline values can reuse these values directly. However, for field
     * variables and variables captured from external scopes, they are hidden as properties
     * of 'this' variable and require additional evaluation to get their values.
     */
    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        InlineValuesArguments inlineValuesArgs = (InlineValuesArguments) arguments;
        final int variableCount = inlineValuesArgs == null || inlineValuesArgs.variables == null ? 0 : inlineValuesArgs.variables.length;
        InlineVariable[] inlineVariables = inlineValuesArgs.variables;
        StackFrameReference stackFrameReference = (StackFrameReference) context.getRecyclableIdPool().getObjectById(inlineValuesArgs.frameId);
        if (stackFrameReference == null) {
            logger.log(Level.SEVERE, String.format("InlineValues failed: invalid stackframe id %d.", inlineValuesArgs.frameId));
            response.body = new Responses.InlineValuesResponse(null);
            return CompletableFuture.completedFuture(response);
        }

        // Async mode is supposed to be performant, then disable the advanced features like inline values.
        if (context.isAttached() && context.asyncJDWP()) {
            response.body = new Responses.InlineValuesResponse(null);
            return CompletableFuture.completedFuture(response);
        }

        IStackFrameManager stackFrameManager = context.getStackFrameManager();
        StackFrame frame = stackFrameManager.getStackFrame(stackFrameReference);
        if (frame == null) {
            logger.log(Level.SEVERE, String.format("InlineValues failed: stale stackframe id %d.", inlineValuesArgs.frameId));
            response.body = new Responses.InlineValuesResponse(null);
            return CompletableFuture.completedFuture(response);
        }

        Variable[] values = new Variable[variableCount];
        try {
            if (isLambdaFrame(frame)) {
                // Lambda expression stores the captured variables from 'outer' scope in a synthetic stackframe below the lambda frame.
                StackFrame syntheticLambdaFrame = stackFrameReference.getThread().frame(stackFrameReference.getDepth() + 1);
                resolveValuesFromThisVariable(syntheticLambdaFrame.thisObject(), inlineVariables, values, true);
            }

            resolveValuesFromThisVariable(frame.thisObject(), inlineVariables, values, false);
        } catch (Exception ex) {
            // do nothig
        }

        Types.Variable[] result = new Types.Variable[variableCount];
        IVariableFormatter variableFormatter = context.getVariableFormatter();
        Map<String, Object> formatterOptions = variableFormatter.getDefaultOptions();
        Map<InlineVariable, Types.Variable> calculatedValues = new HashMap<>();
        IEvaluationProvider evaluationEngine = context.getProvider(IEvaluationProvider.class);
        for (int i = 0; i < variableCount; i++) {
            if (values[i] == null) {
                continue;
            }

            if (calculatedValues.containsKey(inlineVariables[i])) {
                result[i] = calculatedValues.get(inlineVariables[i]);
                continue;
            }

            Value value = values[i].value;
            String name = values[i].name;
            int indexedVariables = -1;
            Value sizeValue = null;
            if (value instanceof ArrayReference) {
                indexedVariables = ((ArrayReference) value).length();
            } else if (value instanceof ObjectReference && DebugSettings.getCurrent().showLogicalStructure && evaluationEngine != null) {
                try {
                    JavaLogicalStructure structure = JavaLogicalStructureManager.getLogicalStructure((ObjectReference) value);
                    if (structure != null && structure.getSizeExpression() != null) {
                        sizeValue = structure.getSize((ObjectReference) value, frame.thread(), evaluationEngine);
                        if (sizeValue != null && sizeValue instanceof IntegerValue) {
                            indexedVariables = ((IntegerValue) sizeValue).value();
                        }
                    }
                } catch (CancellationException | IllegalArgumentException | InterruptedException | ExecutionException | UnsupportedOperationException e) {
                    logger.log(Level.INFO,
                            String.format("Failed to get the logical size for the type %s.", value.type().name()), e);
                }
            }

            Types.Variable formattedVariable = new Types.Variable(name, variableFormatter.valueToString(value, formatterOptions));
            formattedVariable.indexedVariables = Math.max(indexedVariables, 0);
            String detailsValue = null;
            if (sizeValue != null) {
                detailsValue = "size=" + variableFormatter.valueToString(sizeValue, formatterOptions);
            } else if (DebugSettings.getCurrent().showToString) {
                detailsValue = VariableDetailUtils.formatDetailsValue(value, frame.thread(), variableFormatter, formatterOptions, evaluationEngine);
            }

            if (detailsValue != null) {
                formattedVariable.value = formattedVariable.value + " " + detailsValue;
            }

            result[i] = formattedVariable;
            calculatedValues.put(inlineVariables[i], formattedVariable);
        }

        response.body = new Responses.InlineValuesResponse(result);
        return CompletableFuture.completedFuture(response);
    }

    private static boolean isCapturedLocalVariable(String fieldName, String variableName) {
        String capturedVariableName = "val$" + variableName;
        return Objects.equals(fieldName, capturedVariableName)
            || (fieldName.startsWith(capturedVariableName + "$") && NumberUtils.isDigits(fieldName.substring(capturedVariableName.length() + 1)));
    }

    private static boolean isCapturedThisVariable(String fieldName) {
        if (fieldName.startsWith("this$")) {
            String suffix = fieldName.substring(5).replaceAll("\\$+$", "");
            return NumberUtils.isDigits(suffix);
        }

        return false;
    }

    private static boolean isLambdaFrame(StackFrame frame) {
        Method method = frame.location().method();
        return method.isSynthetic() && method.name().startsWith("lambda$");
    }

    private void resolveValuesFromThisVariable(ObjectReference thisObj, InlineVariable[] unresolvedVariables, Variable[] result,
        boolean isSyntheticLambdaFrame) {
        if (thisObj == null) {
            return;
        }

        int unresolved = 0;
        for (Variable item : result) {
            if (item == null) {
                unresolved++;
            }
        }

        try {
            ReferenceType type = thisObj.referenceType();
            String typeName = type.name();
            ObjectReference enclosingInstance = null;
            for (Field field : type.allFields()) {
                String fieldName = field.name();
                boolean isSyntheticField = field.isSynthetic();
                Value fieldValue = null;
                for (int i = 0; i < unresolvedVariables.length; i++) {
                    if (result[i] != null) {
                        continue;
                    }

                    InlineVariable inlineVariable = unresolvedVariables[i];
                    boolean isInlineFieldVariable = (inlineVariable.declaringClass != null);
                    boolean isMatch = false;
                    if (isSyntheticLambdaFrame) {
                        isMatch = !isInlineFieldVariable && Objects.equals(fieldName, inlineVariable.expression);
                    } else {
                        boolean isMatchedField = isInlineFieldVariable
                            && Objects.equals(fieldName, inlineVariable.expression)
                            && Objects.equals(typeName, inlineVariable.declaringClass);
                        boolean isMatchedCapturedVariable = !isInlineFieldVariable
                            && isSyntheticField
                            && isCapturedLocalVariable(fieldName, inlineVariable.expression);
                        isMatch = isMatchedField || isMatchedCapturedVariable;

                        if (!isMatch && isSyntheticField && enclosingInstance == null && isCapturedThisVariable(fieldName)) {
                            Value value = thisObj.getValue(field);
                            if (value instanceof ObjectReference) {
                                enclosingInstance = (ObjectReference) value;
                                break;
                            }
                        }
                    }

                    if (isMatch) {
                        fieldValue = fieldValue == null ? thisObj.getValue(field) : fieldValue;
                        result[i] = new Variable(inlineVariable.expression, fieldValue);
                        unresolved--;
                    }
                }

                if (unresolved <= 0) {
                    break;
                }
            }

            if (unresolved > 0 && enclosingInstance != null) {
                resolveValuesFromThisVariable(enclosingInstance, unresolvedVariables, result, isSyntheticLambdaFrame);
            }
        } catch (Exception ex) {
            // do nothing
        }
    }
}
