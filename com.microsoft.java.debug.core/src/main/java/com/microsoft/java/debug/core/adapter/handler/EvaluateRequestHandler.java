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

package com.microsoft.java.debug.core.adapter.handler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.variables.JdiObjectProxy;
import com.microsoft.java.debug.core.adapter.variables.Variable;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.adapter.variables.VariableUtils;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.EvaluateArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;

public class EvaluateRequestHandler implements IDebugRequestHandler {
    private final Pattern simpleExprPattern = Pattern.compile("[A-Za-z0-9_.\\s]+");

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.EVALUATE);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        EvaluateArguments evalArguments = (EvaluateArguments) arguments;
        if (StringUtils.isBlank(evalArguments.expression)) {
            context.sendErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                    "EvaluateRequest: property 'expression' is missing, null, or empty");
            return;
        }

        final boolean showStaticVariables = DebugSettings.getCurrent().showStaticVariables;

        Map<String, Object> options = context.getVariableFormatter().getDefaultOptions();
        VariableUtils.applyFormatterOptions(options, evalArguments.format != null && evalArguments.format.hex);
        String expression = evalArguments.expression;

        if (StringUtils.isBlank(expression)) {
            context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE, "Failed to evaluate. Reason: Empty expression cannot be evaluated.");
            return;
        }

        if (!simpleExprPattern.matcher(expression).matches()) {
            context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                    "Failed to evaluate. Reason: Complex expression is not supported currently.");
            return;
        }

        JdiObjectProxy<StackFrame> stackFrameProxy = (JdiObjectProxy<StackFrame>) context.getRecyclableIdPool().getObjectById(evalArguments.frameId);
        if (stackFrameProxy == null) {
            // stackFrameProxy is null means the stackframe is continued by user manually,
            context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE, "Failed to evaluate. Reason: Cannot evaluate because the thread is resumed.");
            return;
        }

        // split a.b.c => ["a", "b", "c"]
        List<String> referenceExpressions = Arrays.stream(StringUtils.split(expression, '.'))
                .filter(StringUtils::isNotBlank).map(StringUtils::trim).collect(Collectors.toList());

        // get first level of value from stack frame
        Variable firstLevelValue = null;
        boolean inStaticMethod = stackFrameProxy.getProxiedObject().location().method().isStatic();
        String firstExpression = referenceExpressions.get(0);
        // handle special case of 'this'
        if (firstExpression.equals("this") && !inStaticMethod) {
            firstLevelValue = VariableUtils.getThisVariable(stackFrameProxy.getProxiedObject());
        }
        if (firstLevelValue == null) {
            try {
                // local variables first, that means
                // if both local variable and static variable are found, use local variable
                List<Variable> localVariables = VariableUtils.listLocalVariables(stackFrameProxy.getProxiedObject());
                List<Variable> matchedLocal = localVariables.stream()
                        .filter(localVariable -> localVariable.name.equals(firstExpression)).collect(Collectors.toList());
                if (!matchedLocal.isEmpty()) {
                    firstLevelValue = matchedLocal.get(0);
                } else {
                    List<Variable> staticVariables = VariableUtils.listStaticVariables(stackFrameProxy.getProxiedObject());
                    List<Variable> matchedStatic = staticVariables.stream()
                            .filter(staticVariable -> staticVariable.name.equals(firstExpression)).collect(Collectors.toList());
                    if (matchedStatic.isEmpty()) {
                        context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                                String.format("Failed to evaluate. Reason: Cannot find the variable: %s.", referenceExpressions.get(0)));
                        return;
                    }
                    firstLevelValue = matchedStatic.get(0);
                }

            } catch (AbsentInformationException e) {
                // ignore
            }
        }

        if (firstLevelValue == null) {
            context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                    String.format("Failed to evaluate. Reason: Cannot find variable with name '%s'.", referenceExpressions.get(0)));
            return;
        }
        ThreadReference thread = stackFrameProxy.getProxiedObject().thread();
        Value currentValue = firstLevelValue.value;

        for (int i = 1; i < referenceExpressions.size(); i++) {
            String fieldName = referenceExpressions.get(i);
            if (currentValue == null) {
                context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE, "Failed to evaluate. Reason: Evaluation encounters NPE error.");
                return;
            }
            if (currentValue instanceof PrimitiveValue) {
                context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                        String.format("Failed to evaluate. Reason: Cannot find the field: %s.", fieldName));
                return;
            }
            if (currentValue instanceof ArrayReference) {
                context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                        String.format("Failed to evaluate. Reason: Evaluating array elements is not supported currently.", fieldName));
                return;
            }
            ObjectReference obj = (ObjectReference) currentValue;
            Field field = obj.referenceType().fieldByName(fieldName);
            if (field == null) {
                context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                        String.format("Failed to evaluate. Reason: Cannot find the field: %s.", fieldName));
                return;
            }
            if (field.isStatic()) {
                context.sendErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                        String.format("Failed to evaluate. Reason: Cannot find the field: %s.", fieldName));
                return;
            }
            currentValue = obj.getValue(field);
        }

        int referenceId = 0;
        if (currentValue instanceof ObjectReference && VariableUtils.hasChildren(currentValue, showStaticVariables)) {
            // save the evaluated value in object pool, because like java.lang.String, the evaluated object will have sub structures
            // we need to set up the id map.
            VariableProxy varProxy = new VariableProxy(thread.uniqueID(), "Local", currentValue);
            referenceId = context.getRecyclableIdPool().addObject(thread.uniqueID(), varProxy);
        }
        int indexedVariables = 0;
        if (currentValue instanceof ArrayReference) {
            indexedVariables = ((ArrayReference) currentValue).length();
        }
        response.body = new Responses.EvaluateResponseBody(context.getVariableFormatter().valueToString(currentValue, options),
                referenceId, context.getVariableFormatter().typeToString(currentValue == null ? null : currentValue.type(), options),
                indexedVariables);

        context.sendResponse(response);
    }
}
