/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core;

import java.util.Objects;

import com.microsoft.java.debug.core.protocol.Types;

public class JavaBreakpointLocation {
    /**
     * The source line of the breakpoint or logpoint.
     */
    private int lineNumber;
    /**
     * The source column of the breakpoint.
     */
    private int columnNumber = -1;
    /**
     * The declaring class name that encloses the target position.
     */
    private String className;
    /**
     * The method name and signature when the target position
     * points to a method declaration.
     */
    private String methodName;
    private String methodSignature;
    /**
     * All possible locations for source breakpoints in a given range.
     */
    private Types.BreakpointLocation[] availableBreakpointLocations = new Types.BreakpointLocation[0];

    public JavaBreakpointLocation(int lineNumber, int columnNumber) {
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineNumber, columnNumber, className, methodName, methodSignature);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof JavaBreakpointLocation)) {
            return false;
        }
        JavaBreakpointLocation other = (JavaBreakpointLocation) obj;
        return lineNumber == other.lineNumber && columnNumber == other.columnNumber
                && Objects.equals(className, other.className) && Objects.equals(methodName, other.methodName)
                && Objects.equals(methodSignature, other.methodSignature);
    }

    public int lineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int columnNumber() {
        return columnNumber;
    }

    public void setColumnNumber(int columnNumber) {
        this.columnNumber = columnNumber;
    }

    public String className() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String methodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String methodSignature() {
        return methodSignature;
    }

    public void setMethodSignature(String methodSignature) {
        this.methodSignature = methodSignature;
    }

    public Types.BreakpointLocation[] availableBreakpointLocations() {
        return availableBreakpointLocations;
    }

    public void setAvailableBreakpointLocations(Types.BreakpointLocation[] availableBreakpointLocations) {
        this.availableBreakpointLocations = availableBreakpointLocations;
    }
}
