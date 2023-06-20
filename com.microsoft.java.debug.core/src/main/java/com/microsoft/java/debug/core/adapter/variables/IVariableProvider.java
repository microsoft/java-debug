package com.microsoft.java.debug.core.adapter.variables;

import com.microsoft.java.debug.core.adapter.IProvider;

public interface IVariableProvider extends IProvider {
  /**
   * Get the name for evaluation of variable.
   *
   * @param name           the variable name, if any
   * @param containerName  the container name, if any
   * @param isArrayElement is the variable an array element?
   */
  public String getEvaluateName(String name, String containerName, boolean isArrayElement);
}