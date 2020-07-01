/*******************************************************************************
* Copyright (c) 2017-2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.protocol;

import java.util.List;

import com.microsoft.java.debug.core.protocol.Types.DataBreakpointAccessType;
import com.microsoft.java.debug.core.protocol.Types.ExceptionBreakMode;
import com.microsoft.java.debug.core.protocol.Types.ExceptionDetails;

/**
 * The response content types defined by VSCode Debug Protocol.
 */
public class Responses {
    /**
     * subclasses of ResponseBody are serialized as the response body. Don't
     * change their instance variables since that will break the OpenDebug
     * protocol.
     */
    public static class ResponseBody {
        // empty
    }

    public static class InitializeResponseBody extends ResponseBody {
        public Types.Capabilities body;

        public InitializeResponseBody(Types.Capabilities capabilities) {
            body = capabilities;
        }
    }

    public static class RunInTerminalResponseBody extends ResponseBody {
        public int processId;

        public RunInTerminalResponseBody(int processId) {
            this.processId = processId;
        }
    }

    public static class ErrorResponseBody extends ResponseBody {
        public Types.Message error;

        public ErrorResponseBody(Types.Message m) {
            error = m;
        }
    }

    public static class StackTraceResponseBody extends ResponseBody {
        public Types.StackFrame[] stackFrames;

        public int totalFrames;

        /**
         * Constructs an StackTraceResponseBody with the given stack frame list.
         * @param frames
         *              a {@link Types.StackFrame} list
         * @param total
         *              the total frame number
         */
        public StackTraceResponseBody(List<Types.StackFrame> frames, int total) {
            if (frames == null) {
                stackFrames = new Types.StackFrame[0];
            } else {
                stackFrames = frames.toArray(new Types.StackFrame[0]);
            }

            totalFrames = total;
        }
    }

    public static class ScopesResponseBody extends ResponseBody {
        public Types.Scope[] scopes;

        /**
         * Constructs a ScopesResponseBody with the Scope list.
         * @param scps
         *              a {@link Types.Scope} list
         */
        public ScopesResponseBody(List<Types.Scope> scps) {
            if (scps == null) {
                scopes = new Types.Scope[0];
            } else {
                scopes = scps.toArray(new Types.Scope[0]);
            }
        }
    }

    public static class VariablesResponseBody extends ResponseBody {
        public Types.Variable[] variables;

        /**
         * Constructs a VariablesResponseBody with the given variable list.
         * @param vars
         *              a {@link Types.Variable} list
         */
        public VariablesResponseBody(List<Types.Variable> vars) {
            if (vars == null) {
                variables = new Types.Variable[0];
            } else {
                variables = vars.toArray(new Types.Variable[0]);
            }
        }
    }

    public static class SetVariablesResponseBody extends ResponseBody {
        public String value;
        public String type;
        public int variablesReference;
        public int indexedVariables;

        /**
         * Constructs a SetVariablesResponseBody with the given variable information.
         */
        public SetVariablesResponseBody(String type, String value, int variablesReference, int indexedVariables) {
            this.type = type;
            this.value = value;
            this.variablesReference = variablesReference;
            this.indexedVariables = indexedVariables;
        }
    }

    public static class SourceResponseBody extends ResponseBody {
        public String content;
        public String mimeType = "text/x-java"; // Set mimeType to tell VSCode to recognize the source contents as java source.

        public SourceResponseBody(String content) {
            this.content = content;
        }

        public SourceResponseBody(String content, String mimeType) {
            this.content = content;
            this.mimeType = mimeType;
        }
    }

    public static class ThreadsResponseBody extends ResponseBody {
        public Types.Thread[] threads;

        /**
         * Constructs a ThreadsResponseBody with the given thread list.
         * @param vars
         *            a {@link Types.Thread} list
         */
        public ThreadsResponseBody(List<Types.Thread> vars) {
            if (vars == null) {
                threads = new Types.Thread[0];
            } else {
                threads = vars.toArray(new Types.Thread[0]);
            }
        }
    }

    public static class EvaluateResponseBody extends ResponseBody {
        public String result;
        public int variablesReference;
        public String type;
        public int indexedVariables;

        /**
         * Constructor.
         */
        public EvaluateResponseBody(String value, int ref, String type, int indexedVariables) {
            this.result = value;
            this.variablesReference = ref;
            this.type = type;
            this.indexedVariables = indexedVariables;
        }
    }

    public static class CompletionsResponseBody extends ResponseBody {
        public Types.CompletionItem[] targets;

        /**
         * Constructor.
         */
        public CompletionsResponseBody(List<Types.CompletionItem> items) {
            if (items == null) {
                targets = new Types.CompletionItem[0];
            } else {
                targets = items.toArray(new Types.CompletionItem[0]);
            }
        }
    }

    public static class SetBreakpointsResponseBody extends ResponseBody {
        public Types.Breakpoint[] breakpoints;

        /**
         * Constructs a SetBreakpointsResponssseBody with the given breakpoint list.
         * @param bpts
         *            a {@link Types.Breakpoint} list
         */
        public SetBreakpointsResponseBody(List<Types.Breakpoint> bpts) {
            if (bpts == null) {
                breakpoints = new Types.Breakpoint[0];
            } else {
                breakpoints = bpts.toArray(new Types.Breakpoint[0]);
            }
        }
    }

    public static class SetDataBreakpointsResponseBody extends SetBreakpointsResponseBody {
        public SetDataBreakpointsResponseBody(List<Types.Breakpoint> bpts) {
            super(bpts);
        }
    }

    public static class DataBreakpointInfoResponseBody extends ResponseBody {
        /**
         * An identifier for the data on which a data breakpoint can be registered with the setDataBreakpoints request
         * or null if no data breakpoint is available.
         */
        public String dataId;
        /**
         * UI string that describes on what data the breakpoint is set on or why a data breakpoint is not available.
         */
        public String description;
        /**
         * Optional attribute listing the available access types for a potential data breakpoint. A UI frontend could surface this information.
         */
        public DataBreakpointAccessType[] accessTypes;
        /**
         * Optional attribute indicating that a potential data breakpoint could be persisted across sessions.
         */
        public boolean canPersist;

        public DataBreakpointInfoResponseBody(String dataId) {
            this(dataId, null);
        }

        public DataBreakpointInfoResponseBody(String dataId, String description) {
            this(dataId, description, null);
        }

        public DataBreakpointInfoResponseBody(String dataId, String description,
                DataBreakpointAccessType[] accessTypes) {
            this(dataId, description, accessTypes, false);
        }

        /**
         * Constructor.
         */
        public DataBreakpointInfoResponseBody(String dataId, String description, DataBreakpointAccessType[] accessTypes,
                boolean canPersist) {
            this.dataId = dataId;
            this.description = description;
            this.accessTypes = accessTypes;
            this.canPersist = canPersist;
        }
    }

    public static class ContinueResponseBody extends ResponseBody {
        public boolean allThreadsContinued;

        public ContinueResponseBody() {
            this.allThreadsContinued = true;
        }

        /**
         * Constructs a ContinueResponseBody.
         */
        public ContinueResponseBody(boolean allThreadsContinued) {
            this.allThreadsContinued = allThreadsContinued;
        }
    }

    public static class ExceptionInfoResponse extends ResponseBody {
        public String exceptionId;
        public String description;
        public ExceptionBreakMode breakMode;
        public ExceptionDetails details;

        /**
         * Constructs a ExceptionInfoResponse.
         */
        public ExceptionInfoResponse(String exceptionId, String description, ExceptionBreakMode breakMode) {
            this.exceptionId = exceptionId;
            this.description = description;
            this.breakMode = breakMode;
        }

        /**
         * Constructs a ExceptionInfoResponse.
         */
        public ExceptionInfoResponse(String exceptionId, String description, ExceptionBreakMode breakMode, ExceptionDetails details) {
            this(exceptionId, description, breakMode);
            this.details = details;
        }
    }

    public static class RedefineClassesResponse extends ResponseBody {
        public String[] changedClasses = new String[0];
        public String errorMessage = null;

        /**
         * Constructor.
         */
        public RedefineClassesResponse(String[] changedClasses) {
            this(changedClasses, null);
        }

        /**
         * Constructor.
         */
        public RedefineClassesResponse(String[] changedClasses, String errorMessage) {
            this.changedClasses = changedClasses;
            this.errorMessage = errorMessage;
        }
    }
}
