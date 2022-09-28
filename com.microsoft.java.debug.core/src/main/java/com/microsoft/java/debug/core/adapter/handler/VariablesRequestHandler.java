/*******************************************************************************
* Copyright (c) 2017-2022 Microsoft Corporation and others.
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.AsyncJdwpUtils;
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
import com.microsoft.java.debug.core.adapter.variables.StringReferenceProxy;
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
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public class VariablesRequestHandler implements IDebugRequestHandler {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    /**
     * When the debugger enables logical structures and
     * toString settings, for each Object variable in the
     * variable list, the debugger needs to check its
     * superclass and interface to find out if it inherits
     * from Collection or overrides the toString method.
     * This will cause the debugger to send a lot of JDWP
     * requests for them. For a test case with 4 object
     * variables, the debug adapter may need to send more
     * than 100 JDWP requests to handle these variable
     * requests. To achieve a DAP latency of 1s with a
     * single-threaded JDWP request processing strategy,
     * a single JDWP latency is about 10ms.
     */
    static final long USABLE_JDWP_LATENCY = 10/**ms*/;

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

        if (supportsToStringView(context) && containerNode.isLazyVariable()) {
            Types.Variable typedVariable = this.resolveLazyVariable(context, containerNode, variableFormatter, options, evaluationEngine);
            if (typedVariable != null) {
                list.add(typedVariable);
                response.body = new Responses.VariablesResponseBody(list);
                return CompletableFuture.completedFuture(response);
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

                if (useAsyncJDWP(context)) {
                    childrenList.addAll(getVariablesOfFrameAsync(frame, showStaticVariables));
                } else {
                    childrenList.addAll(VariableUtils.listLocalVariables(frame));
                    Variable thisVariable = VariableUtils.getThisVariable(frame);
                    if (thisVariable != null) {
                        childrenList.add(thisVariable);
                    }
                    if (showStaticVariables && frame.location().method().isStatic()) {
                        childrenList.addAll(VariableUtils.listStaticVariables(frame));
                    }
                }
            } catch (CompletionException | InternalException | InvalidStackFrameException | CancellationException | AbsentInformationException e) {
                throw AdapterUtils.createCompletionException(
                    String.format("Failed to get variables. Reason: %s", e.toString()),
                    ErrorCode.GET_VARIABLE_FAILURE,
                    e.getCause() != null ? e.getCause() : e);
            }
        } else {
            try {
                ObjectReference containerObj = (ObjectReference) containerNode.getProxiedVariable();
                if (supportsLogicStructureView(context) && evaluationEngine != null) {
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
                        childrenList = VariableUtils.listFieldVariables(containerObj, showStaticVariables, useAsyncJDWP(context));
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
        List<Variable> duplicateVars = childrenList.stream()
                .filter(var -> duplicateNames.contains(var.name))
                .collect(Collectors.toList());
        // Since JDI caches the fetched properties locally, in async mode we can warm up the JDI cache in advance.
        if (useAsyncJDWP(context)) {
            try {
                AsyncJdwpUtils.await(warmUpJDICache(childrenList, duplicateVars));
            } catch (CompletionException | CancellationException e) {
                response.body = new Responses.VariablesResponseBody(list);
                return CompletableFuture.completedFuture(response);
            }
        }

        Map<Variable, String> variableNameMap = new HashMap<>();
        if (!duplicateVars.isEmpty()) {
            Map<String, List<Variable>> duplicateVarGroups = duplicateVars.stream()
                    .collect(Collectors.groupingBy(var -> var.name, Collectors.toList()));
            duplicateVarGroups.forEach((k, duplicateVariables) -> {
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
            } else if (supportsLogicStructureView(context) && value instanceof ObjectReference && evaluationEngine != null) {
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

            VariableProxy varProxy = null;
            if (indexedVariables > 0 || (indexedVariables < 0 && value instanceof ObjectReference)) {
                varProxy = new VariableProxy(containerNode.getThread(), containerNode.getScope(), value, containerNode, evaluateName);
                varProxy.setIndexedVariable(indexedVariables >= 0);
                varProxy.setUnboundedType(javaVariable.isUnboundedType());
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

            String detailsValue = null;
            if (hasErrors) {
                // If failed to resolve the variable value, skip the details info as well.
            } else if (sizeValue != null) {
                detailsValue = "size=" + variableFormatter.valueToString(sizeValue, options);
            } else if (supportsToStringView(context)) {
                if (VariableDetailUtils.isLazyLoadingSupported(value) && varProxy != null) {
                    varProxy.setLazyVariable(true);
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

            int referenceId = 0;
            if (varProxy != null) {
                referenceId = context.getRecyclableIdPool().addObject(containerNode.getThreadId(), varProxy);
            }

            Types.Variable typedVariables = new Types.Variable(name, valueString, typeString, referenceId, evaluateName);
            typedVariables.indexedVariables = Math.max(indexedVariables, 0);
            if (varProxy != null && varProxy.isLazyVariable()) {
                typedVariables.presentationHint = new VariablePresentationHint(true);
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

    private boolean supportsLogicStructureView(IDebugAdapterContext context) {
        return (!useAsyncJDWP(context) || context.getJDWPLatency() <= USABLE_JDWP_LATENCY)
            && DebugSettings.getCurrent().showLogicalStructure;
    }

    private boolean supportsToStringView(IDebugAdapterContext context) {
        return (!useAsyncJDWP(context) || context.getJDWPLatency() <= USABLE_JDWP_LATENCY)
            && DebugSettings.getCurrent().showToString;
    }

    private boolean useAsyncJDWP(IDebugAdapterContext context) {
        return context.asyncJDWP(USABLE_JDWP_LATENCY);
    }

    private Types.Variable resolveLazyVariable(IDebugAdapterContext context, VariableProxy containerNode, IVariableFormatter variableFormatter,
            Map<String, Object> options, IEvaluationProvider evaluationEngine) {
        VariableProxy valueReferenceProxy = new VariableProxy(containerNode.getThread(), containerNode.getScope(),
            containerNode.getProxiedVariable(), null /** container */, containerNode.getEvaluateName());
        valueReferenceProxy.setIndexedVariable(containerNode.isIndexedVariable());
        valueReferenceProxy.setUnboundedType(containerNode.isUnboundedType());
        int referenceId = context.getRecyclableIdPool().addObject(containerNode.getThreadId(), valueReferenceProxy);
        // this proxiedVariable is intermediate object, see https://github.com/microsoft/vscode/issues/135147#issuecomment-1076240074
        Object proxiedVariable = containerNode.getProxiedVariable();
        if (proxiedVariable instanceof ObjectReference) {
            ObjectReference variable = (ObjectReference) proxiedVariable;
            String valueString = variableFormatter.valueToString(variable, options);
            String detailString = VariableDetailUtils.formatDetailsValue(variable, containerNode.getThread(), variableFormatter, options,
                evaluationEngine);
            return new Types.Variable("", valueString + " " + detailString, "", referenceId, containerNode.getEvaluateName());
        }
        return null;
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

    private List<Variable> getVariablesOfFrameAsync(StackFrame frame, boolean showStaticVariables) {
        CompletableFuture<List<Variable>> localVariables = VariableUtils.listLocalVariablesAsync(frame);
        CompletableFuture<Variable> thisVariable = VariableUtils.getThisVariableAsync(frame);
        CompletableFuture<List<Variable>>[] staticVariables = new CompletableFuture[1];
        if (showStaticVariables && frame.location().method().isStatic()) {
            staticVariables[0] = VariableUtils.listStaticVariablesAsync(frame);
        }

        CompletableFuture<Void> futures = staticVariables[0] == null ? CompletableFuture.allOf(localVariables, thisVariable)
            : CompletableFuture.allOf(localVariables, thisVariable, staticVariables[0]);

        AsyncJdwpUtils.await(futures);

        List<Variable> result = new ArrayList<>();
        result.addAll(localVariables.join());
        Variable thisVar = thisVariable.join();
        if (thisVar != null) {
            result.add(thisVar);
        }

        if (staticVariables[0] != null) {
            result.addAll(staticVariables[0].join());
        }

        return result;
    }

    private CompletableFuture<Void> warmUpJDICache(List<Variable> variables, List<Variable> duplicatedVars) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        if (duplicatedVars != null && !duplicatedVars.isEmpty()) {
            Set<Type> declaringTypes = new HashSet<>();
            duplicatedVars.forEach((var) -> {
                Type declarationType = var.getDeclaringType();
                if (declarationType != null) {
                    declaringTypes.add(declarationType);
                }
            });

            for (Type type : declaringTypes) {
                if (type instanceof ReferenceType) {
                    // JDWP Command: RT_SIGNATURE
                    futures.add(AsyncJdwpUtils.runAsync(() -> type.signature()));
                }
            }
        }

        for (Variable javaVariable : variables) {
            Value value = javaVariable.value;
            if (value instanceof ArrayReference) {
                // JDWP Command: AR_LENGTH
                futures.add(AsyncJdwpUtils.runAsync(() -> ((ArrayReference) value).length()));
            } else if (value instanceof StringReference) {
                // JDWP Command: SR_VALUE
                futures.add(AsyncJdwpUtils.runAsync(() -> {
                    String strValue = ((StringReference) value).value();
                    javaVariable.value = new StringReferenceProxy((StringReference) value, strValue);
                }));
            }

            if (value instanceof ObjectReference) {
                // JDWP Command: OR_REFERENCE_TYPE, RT_SIGNATURE
                futures.add(AsyncJdwpUtils.runAsync(() -> {
                    value.type().signature();
                }));
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
