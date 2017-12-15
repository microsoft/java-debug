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
import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        EvaluateArguments evalArguments = (EvaluateArguments) arguments;
        if (StringUtils.isBlank(evalArguments.expression)) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                    "EvaluateRequest: property 'expression' is missing, null, or empty");
        }

        final boolean showStaticVariables = DebugSettings.getCurrent().showStaticVariables;

        Map<String, Object> options = context.getVariableFormatter().getDefaultOptions();
        VariableUtils.applyFormatterOptions(options, evalArguments.format != null && evalArguments.format.hex);
        String expression = evalArguments.expression;

        if (StringUtils.isBlank(expression)) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                    "Failed to evaluate. Reason: Empty expression cannot be evaluated.");
        }

        StackFrameProxy stackFrameProxy = (StackFrameProxy) context.getRecyclableIdPool().getObjectById(evalArguments.frameId);
        if (stackFrameProxy == null) {
            // stackFrameProxy is null means the stackframe is continued by user manually,
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                    "Failed to evaluate. Reason: Cannot evaluate because the thread is resumed.");
        }
        IVariableFormatter variableFormatter = context.getVariableFormatter();
        IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
        CompletableFuture<Value> evaluateResult = engine.evaluate(expression, stackFrameProxy.getThread(), stackFrameProxy.getDepth());
        return evaluateResult.thenApply(value -> {
            if (value instanceof VoidValue) {
                response.body = new Responses.EvaluateResponseBody(value.toString(), 0, "<void>", 0);
            } else {
                long threadId = stackFrameProxy.getThread().uniqueID();
                if (value instanceof ObjectReference) {
                    VariableProxy varProxy = new VariableProxy(threadId, "eval", value);
                    int referenceId = VariableUtils.hasChildren(value, showStaticVariables) ? context.getRecyclableIdPool().addObject(threadId, varProxy) : 0;
                    int indexedVariableId = value instanceof ArrayReference ? ((ArrayReference) value).length() : 0;
                    response.body = new Responses.EvaluateResponseBody(variableFormatter.valueToString(value, options), referenceId,
                            variableFormatter.typeToString(value == null ? null : value.type(), options), indexedVariableId);
                } else {
                    // for primitive value
                    response.body = new Responses.EvaluateResponseBody(variableFormatter.valueToString(value, options), 0,
                            variableFormatter.typeToString(value == null ? null : value.type(), options), 0);
                }
            }
            return response;
        });
    }
}
