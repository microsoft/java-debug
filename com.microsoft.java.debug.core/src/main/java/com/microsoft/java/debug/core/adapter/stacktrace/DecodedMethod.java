package com.microsoft.java.debug.core.adapter.stacktrace;

public interface DecodedMethod {
  String format();

  boolean isGenerated();
}
