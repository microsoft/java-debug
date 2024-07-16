package com.microsoft.java.debug.core.adapter.stacktrace;

import java.util.List;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.adapter.formatter.SimpleTypeFormatter;
import com.sun.jdi.Field;

public class JavaField implements DecodedField {
    private Field field;

    public JavaField(Field field) {
      this.field = field;
    }

    @Override
    public String format() {
        return field.name();
    }

    @Override
    public boolean show() {
        return !field.isStatic() || true; // TODO // p2i3: This is a bug. It should be !field.isStatic() || field.isValDef_in_an_Object
    }
}
