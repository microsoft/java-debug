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

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.adapter.variables.StackFrameProxy;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.adapter.variables.VariableUtils;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.EvaluateArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.VoidValue;

public class EvaluateRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.EVALUATE);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        EvaluateArguments evalArguments = (EvaluateArguments) arguments;
        if (StringUtils.isBlank(evalArguments.expression)) {
            AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                    "EvaluateRequest: property 'expression' is missing, null, or empty");
            return;
        }

        final boolean showStaticVariables = DebugSettings.getCurrent().showStaticVariables;

        Map<String, Object> options = context.getVariableFormatter().getDefaultOptions();
        VariableUtils.applyFormatterOptions(options, evalArguments.format != null && evalArguments.format.hex);
        String expression = evalArguments.expression;

        if (StringUtils.isBlank(expression)) {
            AdapterUtils.setErrorResponse(response, ErrorCode.EVALUATE_FAILURE, "Failed to evaluate. Reason: Empty expression cannot be evaluated.");
            return;
        }

        StackFrameProxy stackFrameProxy = (StackFrameProxy) context.getRecyclableIdPool().getObjectById(evalArguments.frameId);
        if (stackFrameProxy == null) {
            // stackFrameProxy is null means the stackframe is continued by user manually,
            AdapterUtils.setErrorResponse(response, ErrorCode.EVALUATE_FAILURE, "Failed to evaluate. Reason: Cannot evaluate because the thread is resumed.");
            return;
        }

        if (context.isStaledState(stackFrameProxy.getStoppedState())) {
            AdapterUtils.setErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                    "Failed to evaluate. Reason: The stack frame is changed.");
            return;
        }


        IVariableFormatter variableFormatter = context.getVariableFormatter();

        IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
        final IDebugAdapterContext finalContext = context;
        finalContext.setResponseAsync(true);
        engine.eval(context.getProjectName(), expression, stackFrameProxy.thread(), stackFrameProxy.getDepth(), (result, error) -> {
            if (error != null) {
                AdapterUtils.setErrorResponse(response, ErrorCode.EVALUATE_FAILURE, "Failed to evaluate. Reason:  " + error.getMessage());
                finalContext.sendResponseAsync(response);
                return;
            }
            Value value = result;
            if (value instanceof VoidValue) {
                response.body = new Responses.EvaluateResponseBody(result.toString(),
                        0, "<void>",
                        0);
            } else {
                long threadId = stackFrameProxy.thread().uniqueID();
                if (value instanceof ObjectReference) {
                    VariableProxy varProxy = new VariableProxy(threadId, "eval", value);
                    int referenceId = VariableUtils.hasChildren(value, showStaticVariables) ? context.getRecyclableIdPool().addObject(threadId, varProxy):0;
                    int indexedVariableId = value instanceof ArrayReference ? ((ArrayReference) value).length() : 0;
                    response.body = new Responses.EvaluateResponseBody(variableFormatter.valueToString(value, options),
                            referenceId, variableFormatter.typeToString(value == null ? null : value.type(), options), indexedVariableId);
                } else {
                    // for primitive value
                    response.body = new Responses.EvaluateResponseBody(variableFormatter.valueToString(value, options),
                            0, variableFormatter.typeToString(value == null ? null : value.type(), options), 0);
                }
            }

            finalContext.sendResponseAsync(response);
        });
    }
}
