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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.DisposableReentrantLock;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.adapter.variables.VariableUtils;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.EvaluateArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Value;
import com.sun.jdi.VoidValue;

public class EvaluateRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.EVALUATE);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        EvaluateArguments evalArguments = (EvaluateArguments) arguments;
        final boolean showStaticVariables = DebugSettings.getCurrent().showStaticVariables;
        Map<String, Object> options = context.getVariableFormatter().getDefaultOptions();
        VariableUtils.applyFormatterOptions(options, evalArguments.format != null && evalArguments.format.hex);
        String expression = evalArguments.expression;

        if (StringUtils.isBlank(expression)) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                    "Failed to evaluate. Reason: Empty expression cannot be evaluated.");
        }
        StackFrameReference stackFrameReference = (StackFrameReference) context.getRecyclableIdPool().getObjectById(evalArguments.frameId);
        if (stackFrameReference == null) {
            // stackFrameReference is null means the stackframe is continued by user manually,
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                    "Failed to evaluate. Reason: Cannot evaluate because the thread is resumed.");
        }

        CompletableFuture<Response> completableFuture = new CompletableFuture<>();
        // new thread to unsure that the logic to acquire locker and release locker will be executed in this thread
        new Thread(() -> {

            IVariableFormatter variableFormatter = context.getVariableFormatter();
            IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
            DisposableReentrantLock<StackFrame>  disposableStackFrame =
                    context.getStackFrameManager().getLockedStackFrame(stackFrameReference.getThread(), stackFrameReference.getDepth());
            if (disposableStackFrame == null) {
                AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                        "Failed to evaluate. Reason: Cannot evaluate because the stackframe cannot be initialized.");
                completableFuture.complete(response);
                return;
            }
            CompletableFuture<Value> evaluateResult  = engine.evaluate(expression, stackFrameReference.getThread(), stackFrameReference.getDepth());
            evaluateResult.whenComplete((value, error) -> {
                if (error != null) {
                    completableFuture.completeExceptionally(error);
                    return;
                }

                if (value instanceof VoidValue) {
                    response.body = new Responses.EvaluateResponseBody(value.toString(), 0, "<void>", 0);
                    completableFuture.complete(response);
                    return;
                }
                long threadId = stackFrameReference.getThread().uniqueID();
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
                completableFuture.complete(response);
            });
            completableFuture.whenComplete((res, er) -> {
                synchronized (disposableStackFrame) {
                    disposableStackFrame.notifyAll();
                }
            });
            synchronized (disposableStackFrame) {
                try {
                    disposableStackFrame.wait();
                    disposableStackFrame.close();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, String.format("Cannot release lock for evalution.", e.toString()), e);
                }
            }

        }).start();
        return completableFuture;
    }
}
