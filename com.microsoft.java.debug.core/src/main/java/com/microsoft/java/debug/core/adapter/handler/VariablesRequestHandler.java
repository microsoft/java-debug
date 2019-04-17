/*******************************************************************************
* Copyright (c) 2017-2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IStackFrameManager;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.LogicalStructureExpression;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.LogicalVariable;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructureManager;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.adapter.variables.Variable;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.adapter.variables.VariableUtils;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.VariablesArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InternalException;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public class VariablesRequestHandler implements IDebugRequestHandler {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.VARIABLES);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        IVariableFormatter variableFormatter = context.getVariableFormatter();
        VariablesArguments varArgs = (VariablesArguments) arguments;

        boolean showStaticVariables = DebugSettings.getCurrent().showStaticVariables;

        Map<String, Object> options = variableFormatter.getDefaultOptions();
        VariableUtils.applyFormatterOptions(options, varArgs.format != null && varArgs.format.hex);

        List<Types.Variable> list = new ArrayList<>();
        Object container = context.getRecyclableIdPool().getObjectById(varArgs.variablesReference);
        // vscode will always send variables request to a staled scope, return the empty list is ok since the next
        // variable request will contain the right variablesReference.
        if (container == null) {
            response.body = new Responses.VariablesResponseBody(list);
            return CompletableFuture.completedFuture(response);
        }

        if (!(container instanceof VariableProxy)) {
            throw AdapterUtils.createCompletionException(
                String.format("VariablesRequest: Invalid variablesReference %d.", varArgs.variablesReference),
                ErrorCode.GET_VARIABLE_FAILURE);
        }

        VariableProxy containerNode = (VariableProxy) container;
        List<Variable> childrenList = new ArrayList<>();
        IStackFrameManager stackFrameManager = context.getStackFrameManager();
        boolean stackFrameStateChanged = false;
        if (containerNode.getProxiedVariable() instanceof StackFrameReference) {
            StackFrameReference stackFrameReference = (StackFrameReference) containerNode.getProxiedVariable();
            StackFrame frame = stackFrameManager.getStackFrame(stackFrameReference);
            if (frame == null) {
                throw AdapterUtils.createCompletionException(
                    String.format("Invalid stackframe id %d to get variables.", varArgs.variablesReference),
                    ErrorCode.GET_VARIABLE_FAILURE);
            }
            try {
                childrenList = VariableUtils.listLocalVariables(frame);
                Variable thisVariable = VariableUtils.getThisVariable(frame);
                if (thisVariable != null) {
                    childrenList.add(thisVariable);
                }
                if (showStaticVariables && frame.location().method().isStatic()) {
                    childrenList.addAll(VariableUtils.listStaticVariables(frame));
                }
            } catch (AbsentInformationException | InternalException | InvalidStackFrameException e) {
                throw AdapterUtils.createCompletionException(
                    String.format("Failed to get variables. Reason: %s", e.toString()),
                    ErrorCode.GET_VARIABLE_FAILURE,
                    e);
            }
        } else {
            try {
                ObjectReference containerObj = (ObjectReference) containerNode.getProxiedVariable();
                IEvaluationProvider evaluationEngine = context.getProvider(IEvaluationProvider.class);
                if (DebugSettings.getCurrent().showLogicalStructure && evaluationEngine != null) {
                    JavaLogicalStructure logicalStructure = JavaLogicalStructureManager.getLogicalStructure(containerObj);
                    while (logicalStructure != null) {
                        LogicalStructureExpression valueExpression = logicalStructure.getValueExpression();
                        LogicalVariable[] logicalVariables = logicalStructure.getVariables();
                        try {
                            if (valueExpression != null) {
                                stackFrameStateChanged = true;
                                Value value = logicalStructure.getValue(containerObj, containerNode.getThread(), evaluationEngine);
                                if (value instanceof ObjectReference) {
                                    containerObj = (ObjectReference) value;
                                    logicalStructure = JavaLogicalStructureManager.getLogicalStructure(containerObj);
                                    continue;
                                } else {
                                    childrenList = Arrays.asList(new Variable("logical structure", value));
                                }
                            } else if (logicalVariables != null && logicalVariables.length > 0) {
                                stackFrameStateChanged = true;
                                for (LogicalVariable logicalVariable : logicalVariables) {
                                    String name = logicalVariable.getName();
                                    Value value = logicalVariable.getValue(containerObj, containerNode.getThread(), evaluationEngine);
                                    childrenList.add(new Variable(name, value));
                                }
                            }
                        } catch (InterruptedException | ExecutionException | InvalidTypeException
                                | ClassNotLoadedException | IncompatibleThreadStateException | InvocationException e) {
                            logger.log(Level.WARNING,
                                    String.format("Failed to get the logical structure for the type %s, fall back to the Object view.",
                                            containerObj.type().name()),
                                    e);
                        }

                        logicalStructure = null;
                    }
                }

                if (childrenList.isEmpty() && VariableUtils.hasChildren(containerObj, showStaticVariables)) {
                    if (varArgs.count > 0) {
                        childrenList = VariableUtils.listFieldVariables(containerObj, varArgs.start, varArgs.count);
                    } else {
                        childrenList = VariableUtils.listFieldVariables(containerObj, showStaticVariables);
                    }
                }
            } catch (AbsentInformationException e) {
                throw AdapterUtils.createCompletionException(
                    String.format("Failed to get variables. Reason: %s", e.toString()),
                    ErrorCode.GET_VARIABLE_FAILURE,
                    e);
            }
        }

        // Find variable name duplicates
        Set<String> duplicateNames = getDuplicateNames(childrenList.stream().map(var -> var.name).collect(Collectors.toList()));
        Map<Variable, String> variableNameMap = new HashMap<>();
        if (!duplicateNames.isEmpty()) {
            Map<String, List<Variable>> duplicateVars = childrenList.stream()
                    .filter(var -> duplicateNames.contains(var.name)).collect(Collectors.groupingBy(var -> var.name, Collectors.toList()));

            duplicateVars.forEach((k, duplicateVariables) -> {
                Set<String> declarationTypeNames = new HashSet<>();
                boolean declarationTypeNameConflict = false;
                // try use type formatter to resolve name conflict
                for (Variable javaVariable : duplicateVariables) {
                    Type declarationType = javaVariable.getDeclaringType();
                    if (declarationType != null) {
                        String declarationTypeName = variableFormatter.typeToString(declarationType, options);
                        String compositeName = String.format("%s (%s)", javaVariable.name, declarationTypeName);
                        if (!declarationTypeNames.add(compositeName)) {
                            declarationTypeNameConflict = true;
                            break;
                        }
                        variableNameMap.put(javaVariable, compositeName);
                    }
                }
                // If there are duplicate names on declaration types, use fully qualified name
                if (declarationTypeNameConflict) {
                    for (Variable javaVariable : duplicateVariables) {
                        Type declarationType = javaVariable.getDeclaringType();
                        if (declarationType != null) {
                            variableNameMap.put(javaVariable, String.format("%s (%s)", javaVariable.name, declarationType.name()));
                        }
                    }
                }
            });
        }
        for (Variable javaVariable : childrenList) {
            Value value = javaVariable.value;
            String name = javaVariable.name;
            if (variableNameMap.containsKey(javaVariable)) {
                name = variableNameMap.get(javaVariable);
            }
            int indexedVariables = -1;
            if (value instanceof ArrayReference) {
                indexedVariables = ((ArrayReference) value).length();
            } else if (value instanceof ObjectReference && DebugSettings.getCurrent().showLogicalStructure
                    && context.getProvider(IEvaluationProvider.class) != null
                    && JavaLogicalStructureManager.isIndexedVariable((ObjectReference) value)) {
                IEvaluationProvider evaluationEngine = context.getProvider(IEvaluationProvider.class);
                try {
                    Value sizeValue = JavaLogicalStructureManager.getLogicalSize((ObjectReference) value, containerNode.getThread(), evaluationEngine);
                    if (sizeValue != null && sizeValue instanceof IntegerValue) {
                        indexedVariables = ((IntegerValue) sizeValue).value();
                    }
                } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException
                        | InvocationException | InterruptedException | ExecutionException | UnsupportedOperationException e) {
                    logger.log(Level.INFO,
                            String.format("Failed to get the logical size for the type %s.", value.type().name()), e);
                }
                stackFrameStateChanged = true;
            }

            int referenceId = 0;
            if (indexedVariables > 0 || (indexedVariables < 0 && VariableUtils.hasChildren(value, showStaticVariables))) {
                VariableProxy varProxy = new VariableProxy(containerNode.getThread(), containerNode.getScope(), value);
                referenceId = context.getRecyclableIdPool().addObject(containerNode.getThreadId(), varProxy);
            }
            Types.Variable typedVariables = new Types.Variable(name, variableFormatter.valueToString(value, options),
                    variableFormatter.typeToString(value == null ? null : value.type(), options),
                    referenceId, null);
            typedVariables.indexedVariables = Math.max(indexedVariables, 0);
            list.add(typedVariables);
        }
        response.body = new Responses.VariablesResponseBody(list);

        if (stackFrameStateChanged) {
            stackFrameManager.reloadStackFrames(containerNode.getThread());
        }
        return CompletableFuture.completedFuture(response);
    }

    private Set<String> getDuplicateNames(Collection<String> list) {
        Set<String> result = new HashSet<>();
        Set<String> set = new HashSet<>();

        for (String item : list) {
            if (!set.contains(item)) {
                set.add(item);
            } else {
                result.add(item);
            }
        }
        return result;
    }
}
