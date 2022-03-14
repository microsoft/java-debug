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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.JdiMethodResult;
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
import com.microsoft.java.debug.core.adapter.variables.VariableDetailUtils;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.adapter.variables.VariableUtils;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.VariablesArguments;
import com.microsoft.java.debug.core.protocol.Types.VariablePresentationHint;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InternalException;
import com.sun.jdi.InvalidStackFrameException;
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
        IEvaluationProvider evaluationEngine = context.getProvider(IEvaluationProvider.class);

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

        if (containerNode.getReferencedVariableId() != null && DebugSettings.getCurrent().showToString) {
            Integer referencedVariableId = containerNode.getReferencedVariableId();
            Object referencedVariable = context.getRecyclableIdPool().getObjectById(referencedVariableId);
            if (referencedVariable instanceof VariableProxy) {
                Object proxiedVariable = ((VariableProxy) referencedVariable).getProxiedVariable();
                if (proxiedVariable instanceof ObjectReference) {
                    ObjectReference variable = (ObjectReference) proxiedVariable;
                    String valueString = variableFormatter.valueToString(variable, options);
                    String detailString = VariableDetailUtils.formatDetailsValue(variable, containerNode.getThread(), variableFormatter, options,
                            evaluationEngine);
                    Types.Variable typedVariable = new Types.Variable("", valueString + " " + detailString, "", referencedVariableId, "");
                    list.add(typedVariable);
                    response.body = new Responses.VariablesResponseBody(list);
                    return CompletableFuture.completedFuture(response);
                }
            }
        }
        List<Variable> childrenList = new ArrayList<>();
        IStackFrameManager stackFrameManager = context.getStackFrameManager();
        String containerEvaluateName = containerNode.getEvaluateName();
        boolean isUnboundedTypeContainer = containerNode.isUnboundedType();
        if (containerNode.getProxiedVariable() instanceof StackFrameReference) {
            StackFrameReference stackFrameReference = (StackFrameReference) containerNode.getProxiedVariable();
            StackFrame frame = stackFrameManager.getStackFrame(stackFrameReference);
            if (frame == null) {
                throw AdapterUtils.createCompletionException(
                    String.format("Invalid stackframe id %d to get variables.", varArgs.variablesReference),
                    ErrorCode.GET_VARIABLE_FAILURE);
            }
            try {
                long threadId = stackFrameReference.getThread().uniqueID();
                JdiMethodResult result = context.getStepResultManager().getMethodResult(threadId);
                if (result != null) {
                    String returnIcon = (AdapterUtils.isWin || AdapterUtils.isMac) ? "⎯►" : "->";
                    childrenList.add(new Variable(returnIcon + result.method.name() + "()", result.value, null));
                }
                childrenList.addAll(VariableUtils.listLocalVariables(frame));
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
                if (DebugSettings.getCurrent().showLogicalStructure && evaluationEngine != null) {
                    JavaLogicalStructure logicalStructure = null;
                    try {
                        logicalStructure = JavaLogicalStructureManager.getLogicalStructure(containerObj);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to get the logical structure for the variable, fall back to the Object view.", e);
                    }
                    if (isUnboundedTypeContainer && logicalStructure != null && containerEvaluateName != null) {
                        containerEvaluateName = "((" + logicalStructure.getFullyQualifiedName() + ")" + containerEvaluateName + ")";
                        isUnboundedTypeContainer = false;
                    }
                    while (logicalStructure != null) {
                        LogicalStructureExpression valueExpression = logicalStructure.getValueExpression();
                        LogicalVariable[] logicalVariables = logicalStructure.getVariables();
                        try {
                            if (valueExpression != null) {
                                containerEvaluateName = containerEvaluateName == null ? null : containerEvaluateName + "." + valueExpression.evaluateName;
                                isUnboundedTypeContainer = valueExpression.returnUnboundedType;
                                Value value = logicalStructure.getValue(containerObj, containerNode.getThread(), evaluationEngine);
                                if (value instanceof ObjectReference) {
                                    containerObj = (ObjectReference) value;
                                    logicalStructure = JavaLogicalStructureManager.getLogicalStructure(containerObj);
                                    continue;
                                } else {
                                    childrenList = Arrays.asList(new Variable("logical structure", value));
                                }
                            } else if (logicalVariables != null && logicalVariables.length > 0) {
                                for (LogicalVariable logicalVariable : logicalVariables) {
                                    String name = logicalVariable.getName();
                                    Value value = logicalVariable.getValue(containerObj, containerNode.getThread(), evaluationEngine);
                                    Variable variable = new Variable(name, value, logicalVariable.getEvaluateName());
                                    variable.setUnboundedType(logicalVariable.returnUnboundedType());
                                    childrenList.add(variable);
                                }
                            }
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Failed to get the logical structure for the variable, fall back to the Object view.", e);
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
            Value sizeValue = null;
            if (value instanceof ArrayReference) {
                indexedVariables = ((ArrayReference) value).length();
            } else if (value instanceof ObjectReference && DebugSettings.getCurrent().showLogicalStructure && evaluationEngine != null) {
                try {
                    JavaLogicalStructure structure = JavaLogicalStructureManager.getLogicalStructure((ObjectReference) value);
                    if (structure != null && structure.getSizeExpression() != null) {
                        sizeValue = structure.getSize((ObjectReference) value, containerNode.getThread(), evaluationEngine);
                        if (sizeValue != null && sizeValue instanceof IntegerValue) {
                            indexedVariables = ((IntegerValue) sizeValue).value();
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.INFO, "Failed to get the logical size of the variable", e);
                }
            }

            String evaluateName = null;
            if (javaVariable.evaluateName == null || (containerEvaluateName == null && containerNode.getProxiedVariable() instanceof ObjectReference)) {
                // Disable evaluate on the method return value.
                evaluateName = null;
            } else if (isUnboundedTypeContainer && !containerNode.isIndexedVariable()) {
                // The type name returned by JDI is the binary name, which uses '$' as the separator of
                // inner class e.g. Foo$Bar. But the evaluation expression only accepts using '.' as the class
                // name separator.
                String typeName = ((ObjectReference) containerNode.getProxiedVariable()).referenceType().name();
                // TODO: This replacement will possibly change the $ in the class name itself.
                typeName = typeName.replaceAll("\\$", ".");
                evaluateName = VariableUtils.getEvaluateName(javaVariable.evaluateName, "((" + typeName + ")" + containerEvaluateName + ")", false);
            } else {
                if (containerEvaluateName != null && containerEvaluateName.contains("%s")) {
                    evaluateName = String.format(containerEvaluateName, javaVariable.evaluateName);
                } else {
                    evaluateName = VariableUtils.getEvaluateName(javaVariable.evaluateName, containerEvaluateName, containerNode.isIndexedVariable());
                }
            }

            int referenceId = 0;
            VariableProxy varProxy = null;
            if (indexedVariables > 0 || (indexedVariables < 0 && value instanceof ObjectReference)) {
                varProxy = this.getVariableProxy(containerNode, value, evaluateName, indexedVariables, javaVariable);
                referenceId = context.getRecyclableIdPool().addObject(containerNode.getThreadId(), varProxy);
            }

            boolean hasErrors = false;
            String valueString = null;
            try {
                valueString = variableFormatter.valueToString(value, options);
            } catch (OutOfMemoryError e) {
                hasErrors = true;
                logger.log(Level.SEVERE, "Failed to convert the value of a large object to a string", e);
                valueString = "<Unable to display the value of a large object>";
            } catch (Exception e) {
                hasErrors = true;
                logger.log(Level.SEVERE, "Failed to resolve the variable value", e);
                valueString = "<Failed to resolve the variable value due to \"" + e.getMessage() + "\">";
            }

            String typeString = "";
            try {
                typeString = variableFormatter.typeToString(value == null ? null : value.type(), options);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to resolve the variable type", e);
                typeString = "";
            }

            Types.Variable typedVariables = new Types.Variable(name, valueString, typeString, referenceId, evaluateName);
            typedVariables.indexedVariables = Math.max(indexedVariables, 0);

            String detailsValue = null;
            if (hasErrors) {
                // If failed to resolve the variable value, skip the details info as well.
            } else if (sizeValue != null) {
                detailsValue = "size=" + variableFormatter.valueToString(sizeValue, options);
            } else if (DebugSettings.getCurrent().showToString) {
                if (VariableDetailUtils.isLazyLoadingSupported(value) && varProxy != null) {
                    typedVariables.presentationHint = new VariablePresentationHint(true);
                    VariableProxy valueReferenceProxy = this.getVariableProxy(containerNode, value, "", indexedVariables, javaVariable);
                    Integer referencedVariableId = context.getRecyclableIdPool().addObject(containerNode.getThreadId(), valueReferenceProxy);
                    varProxy.setReferencedVariableId(referencedVariableId);
                } else {
                    try {
                        detailsValue = VariableDetailUtils.formatDetailsValue(value, containerNode.getThread(), variableFormatter, options, evaluationEngine);
                    } catch (OutOfMemoryError e) {
                        logger.log(Level.SEVERE, "Failed to compute the toString() value of a large object", e);
                        detailsValue = "<Unable to display the details of a large object>";
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to compute the toString() value", e);
                        detailsValue = "<Failed to resolve the variable details due to \"" + e.getMessage() + "\">";
                    }
                }
            }

            if (detailsValue != null) {
                typedVariables.value = typedVariables.value + " " + detailsValue;
            }
            list.add(typedVariables);
        }

        if (list.isEmpty() && containerNode.getProxiedVariable() instanceof ObjectReference) {
            list.add(new Types.Variable("Class has no fields", "", null, 0, null));
        }

        response.body = new Responses.VariablesResponseBody(list);

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

    private VariableProxy getVariableProxy(VariableProxy containerNode, Value value, String evaluateName, int indexedVariables, Variable javaVariable) {
        VariableProxy varProxy = new VariableProxy(containerNode.getThread(), containerNode.getScope(), value, containerNode, evaluateName);
        varProxy.setIndexedVariable(indexedVariables >= 0);
        varProxy.setUnboundedType(javaVariable.isUnboundedType());
        return varProxy;
    }
}
