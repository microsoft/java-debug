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

package com.microsoft.java.debug.core.protocol;

import java.util.Arrays;
import java.util.Map;

/**
 * The request arguments types defined by VSCode Debug Protocol.
 */
public class Requests {

    public static class ValueFormat {
        public boolean hex;
    }

    public static class Arguments {

    }

    public static class InitializeArguments extends Arguments {
        public String clientID;
        public String adapterID;
        public String pathFormat;
        public boolean linesStartAt1;
        public boolean columnsStartAt1;
        public boolean supportsVariableType;
        public boolean supportsVariablePaging;
        public boolean supportsRunInTerminalRequest;
    }

    public static class LaunchArguments extends Arguments {
        public String type;
        public String name;
        public String request;
        public String projectName;
        public String mainClass;
        public String args = "";
        public String vmArgs = "";
        public String encoding = "";
        public String[] classPaths = new String[0];
        public String[] modulePaths = new String[0];
        public String[] sourcePaths = new String[0];
        public String cwd;
        public Map<String, String> env;
        public boolean stopOnEntry;
    }

    public static class AttachArguments extends Arguments {
        public String type;
        public String name;
        public String request;
        public String hostName;
        public int port;
        public int timeout = 30000; // Default to 30s.
        public String[] sourcePaths = new String[0];
        public String projectName;
    }

    public static class RestartArguments extends Arguments {

    }

    public static class DisconnectArguments extends Arguments {
        // If client doesn't set terminateDebuggee attribute at the DisconnectRequest,
        // the debugger would choose to terminate debuggee by default.
        public boolean terminateDebuggee = true;
        public boolean restart;
    }

    public static class ConfigurationDoneArguments extends Arguments {

    }

    public static class SetBreakpointArguments extends Arguments {
        public Types.Source source;
        public int[] lines = new int[0];
        public Types.SourceBreakpoint[] breakpoints = new Types.SourceBreakpoint[0];
        public boolean sourceModified = false;
    }

    public static class StackTraceArguments extends Arguments {
        public long threadId;
        public int startFrame;
        public int levels;
    }

    public static class SetFunctionBreakpointsArguments extends Arguments {
        public Types.FunctionBreakpoint[] breakpoints;
    }

    public static class SetExceptionBreakpointsArguments extends Arguments {
        public String[] filters = new String[0];
    }

    public static class ThreadsArguments extends Arguments {

    }

    public static class ContinueArguments extends Arguments {
        public long threadId;
    }

    public static class NextArguments extends Arguments {
        public long threadId;
    }

    public static class StepInArguments extends Arguments {
        public long threadId;
        public int targetId;
    }

    public static class StepOutArguments extends Arguments {
        public long threadId;
    }

    public static class PauseArguments extends Arguments {
        public long threadId;
    }

    public static class ScopesArguments extends Arguments {
        public int frameId;
    }

    public static class VariablesArguments extends Arguments {
        public int variablesReference = -1;
        public String filter;
        public int start;
        public int count;
        public ValueFormat format;
    }

    public static class SetVariableArguments extends Arguments {
        public int variablesReference;
        public String name;
        public String value;
        public ValueFormat format;
    }

    public static class SourceArguments extends Arguments {
        public int sourceReference;
    }

    public static class EvaluateArguments extends Arguments {
        public String expression;
        public int frameId;
        public String context;
        public ValueFormat format;
    }

    public static class SaveDocumentArguments extends Arguments {
        public String documentUri;
    }

    public static enum Command {
        INITIALIZE("initialize", InitializeArguments.class),
        LAUNCH("launch", LaunchArguments.class),
        ATTACH("attach", AttachArguments.class),
        DISCONNECT("disconnect", DisconnectArguments.class),
        CONFIGURATIONDONE("configurationDone", ConfigurationDoneArguments.class),
        NEXT("next", NextArguments.class),
        CONTINUE("continue", ContinueArguments.class),
        STEPIN("stepIn", StepInArguments.class),
        STEPOUT("stepOut", StepOutArguments.class),
        PAUSE("pause", PauseArguments.class),
        STACKTRACE("stackTrace", StackTraceArguments.class),
        SCOPES("scopes", ScopesArguments.class),
        VARIABLES("variables", VariablesArguments.class),
        SETVARIABLE("setVariable", SetVariableArguments.class),
        SOURCE("source", SourceArguments.class),
        THREADS("threads", ThreadsArguments.class),
        SETBREAKPOINTS("setBreakpoints", SetBreakpointArguments.class),
        SETEXCEPTIONBREAKPOINTS("setExceptionBreakpoints", SetExceptionBreakpointsArguments.class),
        SETFUNCTIONBREAKPOINTS("setFunctionBreakpoints", SetFunctionBreakpointsArguments.class),
        EVALUATE("evaluate", EvaluateArguments.class),
        SAVEDOCUMENT("saveDocument", SaveDocumentArguments.class),
        UNSUPPORTED("", Arguments.class);

        private String command;
        private Class<? extends Arguments> argumentType;

        Command(String command, Class<? extends Arguments> argumentType) {
            this.command = command;
            this.argumentType = argumentType;
        }

        public String toString() {
            return this.command;
        }

        public Class<? extends Arguments> getArgumentType() {
            return this.argumentType;
        }

        /**
         * Get the corresponding Command type by the command name.
         * If the command is not defined in the enum type, return UNSUPPORTED.
         * @param command
         *             the command name
         * @return the Command type
         */
        public static Command parse(String command) {
            Command[] found = Arrays.stream(Command.values()).filter(cmd -> {
                return cmd.toString().equals(command);
            }).toArray(Command[]::new);

            if (found.length > 0) {
                return found[0];
            }
            return UNSUPPORTED;
        }
    }
}
