package com.microsoft.java.debug.core.adapter.stacktrace;

import java.util.List;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.adapter.formatter.SimpleTypeFormatter;
import com.sun.jdi.Method;

public class JavaMethod implements DecodedMethod {
    private Method method;

    public JavaMethod(Method method) {
      this.method = method;
    }

    @Override
    public String format() {
        StringBuilder formattedName = new StringBuilder();
        String fullyQualifiedClassName = method.declaringType().name();
        formattedName.append(SimpleTypeFormatter.trimTypeName(fullyQualifiedClassName));
        formattedName.append(".");
        List<String> argumentTypeNames = method.argumentTypeNames().stream().map(SimpleTypeFormatter::trimTypeName).collect(Collectors.toList());
        formattedName.append("(");
        formattedName.append(String.join(",", argumentTypeNames));
        formattedName.append(")");
        if (method.isNative()) {
            // For native method, display a tip text "native method" in the Call Stack View.
            formattedName.append("[native method]");
        }
        return formattedName.toString();
    }

    @Override
    public boolean isGenerated() {
        return method.isBridge() || method.isSynthetic();
    }
}
