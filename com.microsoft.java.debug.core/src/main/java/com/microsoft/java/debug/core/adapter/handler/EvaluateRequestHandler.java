/*******************************************************************************
* Copyright (c) 2017-2021 Microsoft Corporation and others.
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructureManager;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.adapter.variables.VariableDetailUtils;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.adapter.variables.VariableUtils;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.EvaluateArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.VoidValue;

public class EvaluateRequestHandler implements IDebugRequestHandler {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

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
            throw new CompletionException(AdapterUtils.createUserErrorDebugException(
                "Failed to evaluate. Reason: Empty expression cannot be evaluated.",
                ErrorCode.EVALUATION_COMPILE_ERROR));
        }
        StackFrameReference stackFrameReference = (StackFrameReference) context.getRecyclableIdPool().getObjectById(evalArguments.frameId);
        if (stackFrameReference == null) {
            // stackFrameReference is null means the given thread is running
            throw new CompletionException(AdapterUtils.createUserErrorDebugException(
                    "Evaluation failed because the thread is not suspended.",
                    ErrorCode.EVALUATE_NOT_SUSPENDED_THREAD));
        }

        return CompletableFuture.supplyAsync(() -> {
            IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
            try {
                Value value = engine.evaluate(expression, stackFrameReference.getThread(), stackFrameReference.getDepth()).get();
                IVariableFormatter variableFormatter = context.getVariableFormatter();
                if (value instanceof VoidValue) {
                    response.body = new Responses.EvaluateResponseBody(value.toString(), 0, "<void>", 0);
                    return response;
                }
                long threadId = stackFrameReference.getThread().uniqueID();
                if (value instanceof ObjectReference) {
                    VariableProxy varProxy = new VariableProxy(stackFrameReference.getThread(), "eval", value, null, expression);
                    int indexedVariables = -1;
                    Value sizeValue = null;
                    if (value instanceof ArrayReference) {
                        indexedVariables = ((ArrayReference) value).length();
                    } else if (value instanceof ObjectReference && DebugSettings.getCurrent().showLogicalStructure && engine != null) {
                        try {
                            JavaLogicalStructure structure = JavaLogicalStructureManager.getLogicalStructure((ObjectReference) value);
                            if (structure != null && structure.getSizeExpression() != null) {
                                sizeValue = structure.getSize((ObjectReference) value, stackFrameReference.getThread(), engine);
                                if (sizeValue != null && sizeValue instanceof IntegerValue) {
                                    indexedVariables = ((IntegerValue) sizeValue).value();
                                }
                            }
                        } catch (Exception e) {
                            logger.log(Level.INFO, "Failed to get the logical size of the variable", e);
                        }
                    }
                    int referenceId = 0;
                    if (indexedVariables > 0 || (indexedVariables < 0 && value instanceof ObjectReference)) {
                        referenceId = context.getRecyclableIdPool().addObject(threadId, varProxy);
                    }

                    boolean hasErrors = false;
                    String valueString = null;
                    try {
                        valueString = variableFormatter.valueToString(value, options);
                    } catch (OutOfMemoryError e) {
                        hasErrors = true;
                        logger.log(Level.SEVERE, "Failed to convert the value of a large object to a string", e);
                        valueString = "<Unable to display the value of a large object>";
                    }  catch (Exception e) {
                        hasErrors = true;
                        logger.log(Level.SEVERE, "Failed to resolve the variable value", e);
                        valueString = "<Failed to resolve the variable value due to \"" + e.getMessage() + "\">";
                    }

                    String detailsString = null;
                    if (hasErrors) {
                        // If failed to resolve the variable value, skip the details info as well.
                    } else if (sizeValue != null) {
                        detailsString = "size=" + variableFormatter.valueToString(sizeValue, options);
                    } else if (DebugSettings.getCurrent().showToString) {
                        try {
                            detailsString = VariableDetailUtils.formatDetailsValue(value, stackFrameReference.getThread(), variableFormatter, options, engine);
                        } catch (OutOfMemoryError e) {
                            logger.log(Level.SEVERE, "Failed to compute the toString() value of a large object", e);
                            detailsString = "<Unable to display the details of a large object>";
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Failed to compute the toString() value", e);
                            detailsString = "<Failed to resolve the variable details due to \"" + e.getMessage() + "\">";
                        }
                    }

                    if ("clipboard".equals(evalArguments.context) && detailsString != null) {
                        response.body = new Responses.EvaluateResponseBody(detailsString, -1, "String", 0);
                    } else {
                        String typeString = "";
                        try {
                            typeString = variableFormatter.typeToString(value == null ? null : value.type(), options);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Failed to resolve the variable type", e);
                            typeString = "";
                        }
                        response.body = new Responses.EvaluateResponseBody((detailsString == null) ? valueString : valueString + " " + detailsString,
                                referenceId, typeString, Math.max(indexedVariables, 0));
                    }
                    return response;
                }
                // for primitive value
                response.body = new Responses.EvaluateResponseBody(variableFormatter.valueToString(value, options), 0,
                        variableFormatter.typeToString(value == null ? null : value.type(), options), 0);
                return response;
            } catch (InterruptedException | ExecutionException e) {
                Throwable cause = e;
                if (e instanceof ExecutionException && e.getCause() != null) {
                    cause = e.getCause();
                }

                if (cause instanceof DebugException) {
                    throw new CompletionException(cause);
                }
                throw AdapterUtils.createCompletionException(
                    String.format("Cannot evaluate because of %s.", cause.toString()),
                    ErrorCode.EVALUATE_FAILURE,
                    cause);
            }
        });
    }
}
