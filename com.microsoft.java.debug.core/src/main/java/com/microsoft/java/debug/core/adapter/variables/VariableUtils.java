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

package com.microsoft.java.debug.core.adapter.variables;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.AsyncJdwpUtils;
import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.formatter.NumericFormatEnum;
import com.microsoft.java.debug.core.adapter.formatter.NumericFormatter;
import com.microsoft.java.debug.core.adapter.formatter.SimpleTypeFormatter;
import com.microsoft.java.debug.core.adapter.formatter.StringObjectFormatter;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.InternalException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Type;
import com.sun.jdi.TypeComponent;
import com.sun.jdi.Value;

public abstract class VariableUtils {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    /**
     * Test whether the value has referenced objects.
     *
     * @param value
     *            the value.
     * @param includeStatic
     *            whether or not the static fields are visible.
     * @return true if this value is reference objects.
     */
    public static boolean hasChildren(Value value, boolean includeStatic) {
        if (value == null || !(value instanceof ObjectReference)) {
            return false;
        }
        ReferenceType type = ((ObjectReference) value).referenceType();
        if (type instanceof ArrayType) {
            return ((ArrayReference) value).length() > 0;
        }
        return type.allFields().stream().anyMatch(t -> includeStatic || !t.isStatic());
    }

    /**
     * Get the variables of the object.
     *
     * @param obj
     *            the object
     * @return the variable list
     * @throws AbsentInformationException
     *             when there is any error in retrieving information
     */
    public static List<Variable> listFieldVariables(ObjectReference obj, boolean includeStatic) throws AbsentInformationException {
        List<Variable> res = new ArrayList<>();
        ReferenceType type = obj.referenceType();
        if (type instanceof ArrayType) {
            int arrayIndex = 0;
            boolean isUnboundedArrayType = Objects.equals(type.signature(), "[Ljava/lang/Object;");
            for (Value elementValue : ((ArrayReference) obj).getValues()) {
                Variable ele = new Variable(String.valueOf(arrayIndex++), elementValue);
                ele.setUnboundedType(isUnboundedArrayType);
                res.add(ele);
            }
            return res;
        }
        List<Field> fields = type.allFields().stream().filter(t -> includeStatic || !t.isStatic())
                .sorted((a, b) -> {
                    try {
                        boolean v1isStatic = a.isStatic();
                        boolean v2isStatic = b.isStatic();
                        if (v1isStatic && !v2isStatic) {
                            return -1;
                        }
                        if (!v1isStatic && v2isStatic) {
                            return 1;
                        }
                        return a.name().compareToIgnoreCase(b.name());
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, String.format("Cannot sort fields: %s", e), e);
                        return -1;
                    }
                }).collect(Collectors.toList());

        bulkFetchValues(fields, DebugSettings.getCurrent().limitOfVariablesPerJdwpRequest, (currentPage -> {
            Map<Field, Value> fieldValues = obj.getValues(currentPage);
            for (Field currentField : currentPage) {
                Variable var = new Variable(currentField.name(), fieldValues.get(currentField));
                var.field = currentField;
                res.add(var);
            }
        }));

        return res;
    }

    /**
     * Get the variables of the object with pagination.
     *
     * @param obj
     *            the object
     * @param start
     *            the start of the pagination
     * @param count
     *            the number of variables needed
     * @return the variable list
     * @throws AbsentInformationException
     *             when there is any error in retrieving information
     */
    public static List<Variable> listFieldVariables(ObjectReference obj, int start, int count)
            throws AbsentInformationException {
        List<Variable> res = new ArrayList<>();
        Type type = obj.type();
        if (type instanceof ArrayType) {
            int arrayIndex = start;
            boolean isUnboundedArrayType = Objects.equals(type.signature(), "[Ljava/lang/Object;");
            for (Value elementValue : ((ArrayReference) obj).getValues(start, count)) {
                Variable variable = new Variable(String.valueOf(arrayIndex++), elementValue);
                variable.setUnboundedType(isUnboundedArrayType);
                res.add(variable);
            }
            return res;
        }
        throw new UnsupportedOperationException("Only Array type is supported.");
    }

    /**
     * Get the local variables of an stack frame.
     *
     * @param stackFrame
     *            the stack frame
     * @return local variable list
     * @throws AbsentInformationException
     *             when there is any error in retrieving information
     */
    public static List<Variable> listLocalVariables(StackFrame stackFrame) throws AbsentInformationException {
        List<Variable> res = new ArrayList<>();
        if (stackFrame.location().method().isNative()) {
            return res;
        }
        try {
            List<LocalVariable> visibleVariables = stackFrame.visibleVariables();
            // When using the API StackFrame.getValues() to batch fetch the variable values, the JDI
            // probably throws timeout exception if the variables to be passed at one time are large.
            // So use paging to fetch the values in chunks.
            bulkFetchValues(visibleVariables, DebugSettings.getCurrent().limitOfVariablesPerJdwpRequest, (currentPage -> {
                Map<LocalVariable, Value> values = stackFrame.getValues(currentPage);
                for (LocalVariable localVariable : currentPage) {
                    Variable var = new Variable(localVariable.name(), values.get(localVariable));
                    var.local = localVariable;
                    res.add(var);
                }
            }));
        } catch (AbsentInformationException ex) {
            // avoid listing variable on native methods

            try {
                if (stackFrame.location().method().argumentTypes().size() == 0) {
                    return res;
                }
            } catch (ClassNotLoadedException ex2) {
                // ignore since the method is hit.
            }
            // 1. in oracle implementations, when there is no debug information, the AbsentInformationException will be
            // thrown, then we need to retrieve arguments from stackFrame#getArgumentValues.
            // 2. in eclipse jdt implementations, when there is no debug information, stackFrame#visibleVariables will
            // return some generated variables like arg0, arg1, and the stackFrame#getArgumentValues will return null

            // for both scenarios, we need to handle the possible null returned by stackFrame#getArgumentValues and
            // we need to call stackFrame.getArgumentValues get the arguments if AbsentInformationException is thrown
            int argId = 0;
            try {
                List<Value> arguments = stackFrame.getArgumentValues();
                if (arguments == null) {
                    return res;
                }
                for (Value argValue : arguments) {
                    Variable var = new Variable("arg" + argId, argValue);
                    var.argumentIndex = argId++;
                    res.add(var);
                }
            } catch (InternalException ex2) {
                // From Oracle's forums:
                // This could be a JPDA bug. Unexpected JDWP Error: 32 means that an 'opaque' frame was
                // detected at the lower JPDA levels,
                // typically a native frame.
                if (ex2.errorCode() != 32) {
                    throw ex;
                }
            }
        }
        return res;
    }

    public static CompletableFuture<List<Variable>> listLocalVariablesAsync(StackFrame stackFrame) {
        CompletableFuture<List<Variable>> future = new CompletableFuture<>();
        if (stackFrame.location().method().isNative()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        AsyncJdwpUtils.supplyAsync(() -> {
            try {
                return stackFrame.visibleVariables();
            } catch (AbsentInformationException ex) {
                throw new CompletionException(ex);
            }
        }).thenCompose((visibleVariables) -> {
            // When using the API StackFrame.getValues() to batch fetch the variable values, the JDI
            // probably throws timeout exception if the variables to be passed at one time are large.
            // So use paging to fetch the values in chunks.
            return bulkFetchValuesAsync(visibleVariables, DebugSettings.getCurrent().limitOfVariablesPerJdwpRequest, (currentPage) -> {
                Map<LocalVariable, Value> values = stackFrame.getValues(currentPage);
                List<Variable> result = new ArrayList<>();
                for (LocalVariable localVariable : currentPage) {
                    Variable var = new Variable(localVariable.name(), values.get(localVariable));
                    var.local = localVariable;
                    result.add(var);
                }

                return result;
            });
        }).whenComplete((res, ex) -> {
            if (ex instanceof CompletionException && ex.getCause() != null) {
                ex = ex.getCause();
            }

            if (ex instanceof AbsentInformationException) {
                // avoid listing variable on native methods
                try {
                    if (stackFrame.location().method().argumentTypes().size() == 0) {
                        future.complete(new ArrayList<>());
                        return;
                    }
                } catch (ClassNotLoadedException ex2) {
                    // ignore since the method is hit.
                }
                // 1. in oracle implementations, when there is no debug information, the AbsentInformationException will be
                // thrown, then we need to retrieve arguments from stackFrame#getArgumentValues.
                // 2. in eclipse jdt implementations, when there is no debug information, stackFrame#visibleVariables will
                // return some generated variables like arg0, arg1, and the stackFrame#getArgumentValues will return null

                // for both scenarios, we need to handle the possible null returned by stackFrame#getArgumentValues and
                // we need to call stackFrame.getArgumentValues get the arguments if AbsentInformationException is thrown
                int argId = 0;
                try {
                    List<Value> arguments = stackFrame.getArgumentValues();
                    if (arguments == null) {
                        future.complete(new ArrayList<>());
                        return;
                    }

                    List<Variable> variables = new ArrayList<>();
                    for (Value argValue : arguments) {
                        Variable var = new Variable("arg" + argId, argValue);
                        var.argumentIndex = argId++;
                        variables.add(var);
                    }
                    future.complete(variables);
                } catch (InternalException ex2) {
                    // From Oracle's forums:
                    // This could be a JPDA bug. Unexpected JDWP Error: 32 means that an 'opaque' frame was
                    // detected at the lower JPDA levels,
                    // typically a native frame.
                    if (ex2.errorCode() != 32) {
                        throw ex2;
                    }
                }
            } else if (ex != null) {
                future.complete(new ArrayList<>());
            } else {
                future.complete(res.stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList()));
            }
        });

        return future;
    }

    /**
     * Get the this variable of an stack frame.
     *
     * @param stackFrame
     *            the stack frame
     * @return this variable
     */
    public static Variable getThisVariable(StackFrame stackFrame) {
        ObjectReference thisObject = stackFrame.thisObject();
        if (thisObject == null) {
            return null;
        }
        return new Variable("this", thisObject);
    }

    public static CompletableFuture<Variable> getThisVariableAsync(StackFrame stackFrame) {
        return AsyncJdwpUtils.supplyAsync(() -> {
            ObjectReference thisObject = stackFrame.thisObject();
            if (thisObject == null) {
                return null;
            }
            return new Variable("this", thisObject);
        });
    }

    /**
     * Get the static variable of an stack frame.
     *
     * @param stackFrame
     *            the stack frame
     * @return the static variable of an stack frame.
     */
    public static List<Variable> listStaticVariables(StackFrame stackFrame) {
        List<Variable> res = new ArrayList<>();
        ReferenceType type = stackFrame.location().declaringType();
        List<Field> fields = type.allFields().stream().filter(TypeComponent::isStatic).collect(Collectors.toList());
        bulkFetchValues(fields, DebugSettings.getCurrent().limitOfVariablesPerJdwpRequest, (currentPage -> {
            Map<Field, Value> fieldValues = type.getValues(currentPage);
            for (Field currentField : currentPage) {
                Variable var = new Variable(currentField.name(), fieldValues.get(currentField));
                var.field = currentField;
                res.add(var);
            }
        }));

        return res;
    }

    public static CompletableFuture<List<Variable>> listStaticVariablesAsync(StackFrame stackFrame) {
        CompletableFuture<List<Variable>> future = new CompletableFuture<>();
        ReferenceType type = stackFrame.location().declaringType();
        AsyncJdwpUtils.supplyAsync(() -> {
            return type.allFields().stream().filter(TypeComponent::isStatic).collect(Collectors.toList());
        }).thenCompose((fields) -> {
            return bulkFetchValuesAsync(fields, DebugSettings.getCurrent().limitOfVariablesPerJdwpRequest, (currentPage) -> {
                List<Variable> variables = new ArrayList<>();
                Map<Field, Value> fieldValues = type.getValues(currentPage);
                for (Field currentField : currentPage) {
                    Variable var = new Variable(currentField.name(), fieldValues.get(currentField));
                    var.field = currentField;
                    variables.add(var);
                }

                return variables;
            });
        }).whenComplete((res, ex) -> {
            if (ex instanceof CompletionException && ex.getCause() != null) {
                ex = ex.getCause();
            }

            if (ex != null) {
                future.complete(new ArrayList<>());
            } else {
                future.complete(res.stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList()));
            }
        });

        return future;
    }

    /**
     * Apply the display options for variable formatter, it is used in variable and evaluate requests, controls the display content in
     * variable view/debug console.
     *
     * @param defaultOptions the initial options for adding options from user settings
     * @param hexInArgument when request sent by vscode declare hex format explicitly, settings this parameter true to override value in DebugSettings class.
     */
    public static void applyFormatterOptions(Map<String, Object> defaultOptions, boolean hexInArgument) {
        Map<String, Object> options = defaultOptions;
        boolean showFullyQualifiedNames = DebugSettings.getCurrent().showQualifiedNames;
        if (hexInArgument || DebugSettings.getCurrent().showHex) {
            options.put(NumericFormatter.NUMERIC_FORMAT_OPTION, NumericFormatEnum.HEX);
        }
        if (showFullyQualifiedNames) {
            options.put(SimpleTypeFormatter.QUALIFIED_CLASS_NAME_OPTION, true);
        }

        if (DebugSettings.getCurrent().maxStringLength > 0) {
            options.put(StringObjectFormatter.MAX_STRING_LENGTH_OPTION, DebugSettings.getCurrent().maxStringLength);
        }

        if (DebugSettings.getCurrent().numericPrecision > 0) {
            options.put(NumericFormatter.NUMERIC_PRECISION_OPTION, DebugSettings.getCurrent().numericPrecision);
        }
    }

    /**
     * Get the name for evaluation of variable.
     *
     * @param name the variable name, if any
     * @param containerName the container name, if any
     * @param isArrayElement is the variable an array element?
     */
    public static String getEvaluateName(String name, String containerName, boolean isArrayElement) {
        if (name == null) {
            return null;
        }

        if (isArrayElement) {
            if (containerName == null) {
                return null;
            }

            return String.format("%s[%s]", containerName, name);
        }

        if (containerName == null) {
            return name;
        }

        return String.format("%s.%s", containerName, name);
    }

    private static <T> void bulkFetchValues(List<T> elements, int numberPerPage, Consumer<List<T>> consumer) {
        int size = elements.size();
        numberPerPage = numberPerPage < 1 ? 1 : numberPerPage;
        int page = size / numberPerPage + Math.min(size % numberPerPage, 1);
        for (int i = 0; i < page; i++) {
            int pageStart = i * numberPerPage;
            int pageEnd = Math.min(pageStart + numberPerPage, size);
            List<T> currentPage = elements.subList(pageStart, pageEnd);
            consumer.accept(currentPage);
        }
    }

    private static <T, R> CompletableFuture<List<R>> bulkFetchValuesAsync(List<T> elements, int numberPerPage, Function<List<T>, R> function) {
        int size = elements.size();
        numberPerPage = numberPerPage < 1 ? 1 : numberPerPage;
        int page = size / numberPerPage + Math.min(size % numberPerPage, 1);
        List<CompletableFuture<R>> futures = new ArrayList<>();
        for (int i = 0; i < page; i++) {
            int pageStart = i * numberPerPage;
            int pageEnd = Math.min(pageStart + numberPerPage, size);
            final List<T> currentPage = elements.subList(pageStart, pageEnd);
            futures.add(AsyncJdwpUtils.supplyAsync(() -> {
                return function.apply(currentPage);
            }));
        }

        return AsyncJdwpUtils.all(futures);
    }

    private VariableUtils() {

    }
}
