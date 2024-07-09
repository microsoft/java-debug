package com.microsoft.java.debug.core.adapter.stacktrace;

import java.util.List;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.adapter.formatter.SimpleTypeFormatter;
import com.sun.jdi.LocalVariable;

public class JavaLocalVariable implements DecodedVariable {
    private LocalVariable variable;

    public JavaLocalVariable(LocalVariable variable) {
      this.variable = variable;
    }

    @Override
    public String format() {
        return variable.name();
    }
}
