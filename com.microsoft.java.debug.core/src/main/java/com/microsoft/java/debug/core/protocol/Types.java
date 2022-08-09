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

package com.microsoft.java.debug.core.protocol;

import java.nio.file.Paths;

import com.google.gson.annotations.SerializedName;

/**
 * The data types defined by Debug Adapter Protocol.
 */
public class Types {
    public static class Message {
        public int id;
        public String format;

        /**
         * Constructs a message with the given information.
         *
         * @param id
         *          message id
         * @param format
         *          a format string
         */
        public Message(int id, String format) {
            this.id = id;
            this.format = format;
        }
    }

    public static class StackFrame {
        public int id;
        public Source source;
        public int line;
        public int column;
        public String name;
        public String presentationHint;


        /**
         * Constructs a StackFrame with the given information.
         *
         * @param id
         *          the stack frame id
         * @param name
         *          the stack frame name
         * @param src
         *          source info of the stack frame
         * @param ln
         *          line number of the stack frame
         * @param col
         *          column number of the stack frame
         * @param presentationHint
         *          An optional hint for how to present this frame in the UI.
         *          Values: 'normal', 'label', 'subtle'
         */
        public StackFrame(int id, String name, Source src, int ln, int col, String presentationHint) {
            this.id = id;
            this.name = name;
            this.source = src;
            this.line = ln;
            this.column = col;
            this.presentationHint = presentationHint;
        }
    }

    public static class Scope {
        public String name;
        public int variablesReference;
        public boolean expensive;

        /**
         * Constructor.
         */
        public Scope(String name, int rf, boolean exp) {
            this.name = name;
            this.variablesReference = rf;
            this.expensive = exp;
        }
    }

    public static class Variable {
        public String name;
        public String value;
        public String type;
        public int variablesReference;
        public int namedVariables;
        public int indexedVariables;
        public String evaluateName;
        public VariablePresentationHint presentationHint;

        /**
         * Constructor.
         */
        public Variable(String name, String val, String type, int rf, String evaluateName) {
            this.name = name;
            this.value = val;
            this.type = type;
            this.variablesReference = rf;
            this.evaluateName = evaluateName;
        }

        /**
         * Constructor.
         */
        public Variable(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class Thread {
        public long id;
        public String name;

        /**
         * Constructor.
         */
        public Thread(long l, String name) {
            this.id = l;
            if (name == null || name.length() == 0) {
                this.name = String.format("Thread #%d", l);
            } else {
                this.name = name;
            }
        }
    }

    public static class Source {
        public String name;
        public String path;
        public int sourceReference;

        public Source() {
        }

        /**
         * Constructor.
         */
        public Source(String name, String path, int rf) {
            this.name = name;
            this.path = path;
            this.sourceReference = rf;
        }

        /**
         * Constructor.
         */
        public Source(String path, int rf) {
            this.name = Paths.get(path).getFileName().toString();
            this.path = path;
            this.sourceReference = rf;
        }
    }

    public static class Breakpoint {
        /**
         * An optional identifier for the breakpoint. It is needed if breakpoint events are used to update or remove breakpoints.
         */
        public int id;
        /**
         * If true breakpoint could be set (but not necessarily at the desired location).
         */
        public boolean verified;
        /**
         * The start line of the actual range covered by the breakpoint.
         */
        public int line;
        /**
         * An optional message about the state of the breakpoint. This is shown to the user and can be used to explain why a breakpoint could not be verified.
         */
        public String message;

        public Breakpoint(boolean verified) {
            this.verified = verified;
        }

        public Breakpoint(int id, boolean verified) {
            this.id = id;
            this.verified = verified;
        }

        /**
         * Constructor.
         */
        public Breakpoint(int id, boolean verified, int line, String message) {
            this.id = id;
            this.verified = verified;
            this.line = line;
            this.message = message;
        }
    }

    /**
     * Properties of a breakpoint or logpoint passed to the setBreakpoints request.
     */
    public static class SourceBreakpoint {
        public int line;
        public int column;
        public String hitCondition;
        public String condition;
        public String logMessage;

        public SourceBreakpoint(int line, int column) {
            this.line = line;
            this.column = column;
        }

        /**
         * Constructor.
         */
        public SourceBreakpoint(int line, String condition, String hitCondition) {
            this.line = line;
            this.condition = condition;
            this.hitCondition = hitCondition;
        }

        /**
         * Constructor.
         */
        public SourceBreakpoint(int line, String condition, String hitCondition, int column) {
            this.line = line;
            this.column = column;
            this.condition = condition;
            this.hitCondition = hitCondition;
        }
    }

    public static class FunctionBreakpoint {
        public String name;
        public String condition;
        public String hitCondition;

        public FunctionBreakpoint() {
        }

        public FunctionBreakpoint(String name) {
            this.name = name;
        }
    }

    public static enum DataBreakpointAccessType {
        @SerializedName("read")
        READ("read"),
        @SerializedName("write")
        WRITE("write"),
        @SerializedName("readWrite")
        READWRITE("readWrite");

        String label;

        DataBreakpointAccessType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static class DataBreakpoint {
        /**
         * An id representing the data. This id is returned from the dataBreakpointInfo request.
         */
        public String dataId;
        /**
         * The access type of the data.
         */
        public DataBreakpointAccessType accessType;
        /**
         * An optional expression for conditional breakpoints.
         */
        public String condition;
        /**
         * An optional expression that controls how many hits of the breakpoint are ignored. The backend is expected to interpret the expression as needed.
         */
        public String hitCondition;

        public DataBreakpoint(String dataId) {
            this.dataId = dataId;
        }

        public DataBreakpoint(String dataId, DataBreakpointAccessType accessType) {
            this.dataId = dataId;
            this.accessType = accessType;
        }

        /**
         * Constructor.
         */
        public DataBreakpoint(String dataId, DataBreakpointAccessType accessType, String condition, String hitCondition) {
            this.dataId = dataId;
            this.accessType = accessType;
            this.condition = condition;
            this.hitCondition = hitCondition;
        }
    }

    /**
     * Properties of a breakpoint location returned from the breakpointLocations request.
     */
    public static class BreakpointLocation {
        /**
         * Start line of breakpoint location.
         */
        public int line;

        /**
         * The start column of breakpoint location.
         */
        public int column;

        /**
         * The end line of breakpoint location if the location covers a range.
         */
        public int endLine;

        /**
         * The end column of breakpoint location if the location covers a range.
         */
        public int endColumn;

        public BreakpointLocation() {
        }

        public BreakpointLocation(int line, int column) {
            this.line = line;
            this.column = column;
        }

        public BreakpointLocation(int line, int column, int endLine, int endColumn) {
            this.line = line;
            this.column = column;
            this.endLine = endLine;
            this.endColumn = endColumn;
        }
    }

    public static class CompletionItem {
        public String label;
        public String text;
        public String type;
        /**
         * A string that should be used when comparing this item with other items.
         */
        public String sortText;

        public int start;
        public int number;

        public CompletionItem() {
        }

        public CompletionItem(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    public static class ExceptionBreakpointFilter {
        public static final String UNCAUGHT_EXCEPTION_FILTER_NAME = "uncaught";
        public static final String CAUGHT_EXCEPTION_FILTER_NAME = "caught";
        public static final String UNCAUGHT_EXCEPTION_FILTER_LABEL = "Uncaught Exceptions";
        public static final String CAUGHT_EXCEPTION_FILTER_LABEL = "Caught Exceptions";

        public String label;
        public String filter;

        public ExceptionBreakpointFilter(String value, String label) {
            this.filter = value;
            this.label = label;
        }

        public static final ExceptionBreakpointFilter UNCAUGHT_EXCEPTION_FILTER =
                new ExceptionBreakpointFilter(UNCAUGHT_EXCEPTION_FILTER_NAME, UNCAUGHT_EXCEPTION_FILTER_LABEL);
        public static final ExceptionBreakpointFilter CAUGHT_EXCEPTION_FILTER =
                new ExceptionBreakpointFilter(CAUGHT_EXCEPTION_FILTER_NAME, CAUGHT_EXCEPTION_FILTER_LABEL);
    }

    public static enum ExceptionBreakMode {
        @SerializedName("never")
        NEVER,
        @SerializedName("always")
        ALWAYS,
        @SerializedName("unhandled")
        UNHANDLED,
        @SerializedName("userUnhandled")
        USERUNHANDLED
    }

    public static class ExceptionDetails {
        public String message;
        public String typeName;
        public String fullTypeName;
        public String evaluateName;
        public String stackTrace;
        public ExceptionDetails[] innerException;
    }

    public static class VariablePresentationHint {
        public boolean lazy;

        public VariablePresentationHint(boolean lazy) {
            this.lazy = lazy;
        }
    }

    public static class Capabilities {
        public boolean supportsConfigurationDoneRequest;
        public boolean supportsHitConditionalBreakpoints;
        public boolean supportsConditionalBreakpoints;
        public boolean supportsEvaluateForHovers;
        public boolean supportsCompletionsRequest;
        public boolean supportsRestartFrame;
        public boolean supportsSetVariable;
        public boolean supportsRestartRequest;
        public boolean supportTerminateDebuggee;
        public boolean supportsDelayedStackTraceLoading;
        public boolean supportsLogPoints;
        public boolean supportsExceptionInfoRequest;
        public ExceptionBreakpointFilter[] exceptionBreakpointFilters = new ExceptionBreakpointFilter[0];
        public boolean supportsDataBreakpoints;
        public boolean supportsClipboardContext;
        public boolean supportsFunctionBreakpoints;
        // https://microsoft.github.io/debug-adapter-protocol/specification#Requests_BreakpointLocations
        public boolean supportsBreakpointLocationsRequest;
        public boolean supportsStepInTargetsRequest;
    }

    public static class StepInTarget {
        public int id;
        public String label;
        public int line;
        public int column;
        public int endLine;
        public int endColumn;

        public StepInTarget(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }

}
