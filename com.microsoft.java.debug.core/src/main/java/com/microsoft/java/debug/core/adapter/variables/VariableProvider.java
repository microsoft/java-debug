package com.microsoft.java.debug.core.adapter.variables;

public class VariableProvider implements IVariableProvider {
    @Override
    public String getEvaluateName(String name, String containerName, boolean isArrayElement) {
        return VariableUtils.getEvaluateName(name, containerName, isArrayElement);
    }
}