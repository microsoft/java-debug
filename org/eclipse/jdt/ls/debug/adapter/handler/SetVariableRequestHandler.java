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

package org.eclipse.jdt.ls.debug.adapter.handler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.ls.debug.adapter.AdapterUtils;
import org.eclipse.jdt.ls.debug.adapter.ErrorCode;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.Requests.SetVariableArguments;
import org.eclipse.jdt.ls.debug.adapter.Responses;
import org.eclipse.jdt.ls.debug.adapter.formatter.NumericFormatEnum;
import org.eclipse.jdt.ls.debug.adapter.formatter.NumericFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.SimpleTypeFormatter;
import org.eclipse.jdt.ls.debug.adapter.variables.VariableProxy;
import org.eclipse.jdt.ls.debug.adapter.variables.VariableUtils;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Type;
import com.sun.jdi.TypeComponent;
import com.sun.jdi.Value;

public class SetVariableRequestHandler implements IDebugRequestHandler {
    private static final String PATTERN = "([a-zA-Z_0-9$]+)\\s*\\(([^)]+)\\)";
    private IDebugAdapterContext context = null;

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SETVARIABLE);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        SetVariableArguments setVarArguments = (SetVariableArguments) arguments;
        if (setVarArguments.value == null) {
            // Just exit out of editing if we're given an empty expression.
            return;
        } else if (setVarArguments.variablesReference == -1) {
            AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                    "SetVariablesRequest: property 'variablesReference' is missing, null, or empty");
            return;
        } else if (StringUtils.isBlank(setVarArguments.name)) {
            AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                    "SetVariablesRequest: property 'name' is missing, null, or empty");
            return;
        }

        this.context = context;
        Map<String, Object> options = context.getVariableFormatter().getDefaultOptions();
        // This should be false by default(currently true for test).
        // User will need to explicitly turn it on by configuring launch.json
        boolean showStaticVariables = true;
        // TODO: when vscode protocol support customize settings of value format, showFullyQualifiedNames should be one of the options.
        boolean showFullyQualifiedNames = true;
        if (setVarArguments.format != null && setVarArguments.format.hex) {
            options.put(NumericFormatter.NUMERIC_FORMAT_OPTION, NumericFormatEnum.HEX);
        }
        if (showFullyQualifiedNames) {
            options.put(SimpleTypeFormatter.QUALIFIED_CLASS_NAME_OPTION, showFullyQualifiedNames);
        }

        Object container = context.getRecyclableIdPool().getObjectById(setVarArguments.variablesReference);
        // container is null means the stack frame is continued by user manually.
        if (container == null) {
            AdapterUtils.setErrorResponse(response, ErrorCode.SET_VARIABLE_FAILURE,
                    "Failed to set variable. Reason: Cannot set value because the thread is resumed.");
            return;
        }

        String name = setVarArguments.name;
        Value newValue;
        String belongToClass = null;

        if (setVarArguments.name.contains("(")) {
            name = setVarArguments.name.replaceFirst(PATTERN, "$1");
            belongToClass = setVarArguments.name.replaceFirst(PATTERN, "$2");
        }

        Object containerObj = ((VariableProxy) container).getProxiedVariable();
        try {
            if (containerObj instanceof StackFrame) {
                newValue = handleSetValueForStackFrame(name, belongToClass, setVarArguments.value,
                        showStaticVariables, (StackFrame) containerObj, options);
            } else if (containerObj instanceof ObjectReference) {
                newValue = handleSetValueForObject(name, belongToClass, setVarArguments.value,
                        (ObjectReference) containerObj, options);
            } else {
                AdapterUtils.setErrorResponse(response, ErrorCode.SET_VARIABLE_FAILURE,
                        String.format("SetVariableRequest: Variable %s cannot be found.", setVarArguments.variablesReference));
                return;
            }
        } catch (IllegalArgumentException | AbsentInformationException | InvalidTypeException
                | UnsupportedOperationException | ClassNotLoadedException e) {
            AdapterUtils.setErrorResponse(response, ErrorCode.SET_VARIABLE_FAILURE,
                    String.format("Failed to set variable. Reason: %s", e.toString()));
            return;
        }
        int referenceId = 0;
        if (newValue instanceof ObjectReference && VariableUtils.hasChildren(newValue, showStaticVariables)) {
            long threadId = ((VariableProxy) container).getThreadId();
            String scopeName = ((VariableProxy) container).getScope();
            VariableProxy varProxy = new VariableProxy(threadId, scopeName, (ObjectReference) newValue);
            referenceId = context.getRecyclableIdPool().addObject(threadId, varProxy);
        }

        int indexedVariables = 0;
        if (newValue instanceof ArrayReference) {
            indexedVariables = ((ArrayReference) newValue).length();
        }
        response.body = new Responses.SetVariablesResponseBody(
                context.getVariableFormatter().typeToString(newValue == null ? null : newValue.type(), options), // type
                context.getVariableFormatter().valueToString(newValue, options), // value,
                referenceId, indexedVariables);
    }

    private Value handleSetValueForObject(String name, String belongToClass, String valueString,
            ObjectReference container, Map<String, Object> options) throws InvalidTypeException, ClassNotLoadedException {
        Value newValue;
        if (container instanceof ArrayReference) {
            ArrayReference array = (ArrayReference) container;
            Type eleType = ((ArrayType) array.referenceType()).componentType();
            newValue = setArrayValue(array, eleType, Integer.parseInt(name), valueString, options);
        } else {
            if (StringUtils.isBlank(belongToClass)) {
                Field field = container.referenceType().fieldByName(name);
                if (field != null) {
                    if (field.isStatic()) {
                        newValue = this.setStaticFieldValue(container.referenceType(), field, name, valueString, options);
                    } else {
                        newValue = this.setObjectFieldValue(container, field, name, valueString, options);
                    }
                } else {
                    throw new IllegalArgumentException(
                            String.format("SetVariableRequest: Variable %s cannot be found.", name));
                }
            } else {
                newValue = setFieldValueWithConflict(container, container.referenceType().allFields(), name, belongToClass, valueString, options);
            }
        }
        return newValue;
    }

    private Value handleSetValueForStackFrame(String name, String belongToClass, String valueString,
            boolean showStaticVariables, StackFrame container, Map<String, Object> options)
                    throws AbsentInformationException, InvalidTypeException, ClassNotLoadedException {
        Value newValue;
        if (name.equals("this")) {
            throw new UnsupportedOperationException("SetVariableRequest: 'This' variable cannot be changed.");
        }
        LocalVariable variable = container.visibleVariableByName(name);
        if (StringUtils.isBlank(belongToClass) && variable != null) {
            newValue = this.setFrameValue(container, variable, valueString, options);
        } else {
            if (showStaticVariables && container.location().method().isStatic()) {
                ReferenceType type = container.location().declaringType();
                if (StringUtils.isBlank(belongToClass)) {
                    Field field = type.fieldByName(name);
                    newValue = setStaticFieldValue(type, field, name, valueString, options);
                } else {
                    newValue = setFieldValueWithConflict(null, type.allFields(), name, belongToClass,
                            valueString, options);
                }
            } else {
                throw new UnsupportedOperationException(
                        String.format("SetVariableRequest: Variable %s cannot be found.", name));
            }
        }
        return newValue;
    }

    private Value setValueProxy(Type type, String value, SetValueFunction setValueFunc, Map<String, Object> options)
            throws ClassNotLoadedException, InvalidTypeException {
        Value newValue = context.getVariableFormatter().stringToValue(value, type, options);
        setValueFunc.apply(newValue);
        return newValue;
    }

    private Value setStaticFieldValue(Type declaringType, Field field, String name, String value, Map<String, Object> options)
            throws ClassNotLoadedException, InvalidTypeException {
        if (field.isFinal()) {
            throw new UnsupportedOperationException(
                    String.format("SetVariableRequest: Final field %s cannot be changed.", name));
        }
        if (!(declaringType instanceof ClassType)) {
            throw new UnsupportedOperationException(
                    String.format("SetVariableRequest: Field %s in interface cannot be changed.", name));
        }
        return setValueProxy(field.type(), value, newValue -> ((ClassType) declaringType).setValue(field, newValue), options);
    }

    private Value setFrameValue(StackFrame frame, LocalVariable localVariable, String value, Map<String, Object> options)
            throws ClassNotLoadedException, InvalidTypeException {
        return setValueProxy(localVariable.type(), value, newValue -> frame.setValue(localVariable, newValue), options);
    }

    private Value setObjectFieldValue(ObjectReference obj, Field field, String name, String value, Map<String, Object> options)
            throws ClassNotLoadedException, InvalidTypeException {
        if (field.isFinal()) {
            throw new UnsupportedOperationException(
                    String.format("SetVariableRequest: Final field %s cannot be changed.", name));
        }
        return setValueProxy(field.type(), value, newValue -> obj.setValue(field, newValue), options);
    }

    private Value setArrayValue(ArrayReference array, Type eleType, int index, String value, Map<String, Object> options)
            throws ClassNotLoadedException, InvalidTypeException {
        return setValueProxy(eleType, value, newValue -> array.setValue(index, newValue), options);
    }

    private Value setFieldValueWithConflict(ObjectReference obj, List<Field> fields, String name, String belongToClass,
                                            String value, Map<String, Object> options) throws ClassNotLoadedException, InvalidTypeException {
        Field field;
        // first try to resolve field by fully qualified name
        List<Field> narrowedFields = fields.stream().filter(TypeComponent::isStatic)
                .filter(t -> t.name().equals(name) && t.declaringType().name().equals(belongToClass))
                .collect(Collectors.toList());
        if (narrowedFields.isEmpty()) {
            // second try to resolve field by formatted name
            narrowedFields = fields.stream().filter(TypeComponent::isStatic)
                    .filter(t -> t.name().equals(name)
                            && context.getVariableFormatter().typeToString(t.declaringType(), options).equals(belongToClass))
                    .collect(Collectors.toList());
        }
        if (narrowedFields.size() == 1) {
            field = narrowedFields.get(0);
        } else {
            throw new UnsupportedOperationException(String.format("SetVariableRequest: Name conflicted for %s.", name));
        }
        return field.isStatic() ? setStaticFieldValue(field.declaringType(), field, name, value, options)
                : this.setObjectFieldValue(obj, field, name, value, options);
    }

    @FunctionalInterface
    interface SetValueFunction {
        void apply(Value value) throws InvalidTypeException, ClassNotLoadedException;
    }
}
