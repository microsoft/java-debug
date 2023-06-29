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

import com.google.gson.annotations.SerializedName;
import com.microsoft.java.debug.core.protocol.Types.Source;

/**
 * The event types defined by VSCode Debug Protocol.
 */
public class Events {
    public static class DebugEvent {
        public String type;

        public DebugEvent(String type) {
            this.type = type;
        }
    }

    public static class InitializedEvent extends DebugEvent {
        public InitializedEvent() {
            super("initialized");
        }
    }

    public static class StoppedEvent extends DebugEvent {
        public long threadId;
        public String reason;
        public String description;
        public String text;
        public boolean allThreadsStopped;

        /**
         * Constructor.
         */
        public StoppedEvent(String reason, long threadId) {
            super("stopped");
            this.reason = reason;
            this.threadId = threadId;
            allThreadsStopped = false;
        }

        /**
         * Constructor.
         */
        public StoppedEvent(String reason, long threadId, boolean allThreadsStopped) {
            this(reason, threadId);
            this.allThreadsStopped = allThreadsStopped;
        }

        /**
         * Constructor.
         */
        public StoppedEvent(String reason, long threadId, boolean allThreadsStopped, String description, String text) {
            this(reason, threadId, allThreadsStopped);
            this.description = description;
            this.text = text;
        }
    }

    public static class ContinuedEvent extends DebugEvent {
        public long threadId;
        public boolean allThreadsContinued;

        /**
         * Constructor.
         */
        public ContinuedEvent(long threadId) {
            super("continued");
            this.threadId = threadId;
        }

        /**
         * Constructor.
         */
        public ContinuedEvent(long threadId, boolean allThreadsContinued) {
            this(threadId);
            this.allThreadsContinued = allThreadsContinued;
        }

        /**
         * Constructor.
         */
        public ContinuedEvent(boolean allThreadsContinued) {
            super("continued");
            this.allThreadsContinued = allThreadsContinued;
        }
    }

    public static class ExitedEvent extends DebugEvent {
        public int exitCode;

        public ExitedEvent(int code) {
            super("exited");
            this.exitCode = code;
        }
    }

    public static class TerminatedEvent extends DebugEvent {
        public boolean restart;

        public TerminatedEvent() {
            super("terminated");
        }

        public TerminatedEvent(boolean restart) {
            this();
            this.restart = restart;
        }
    }

    public static class ThreadEvent extends DebugEvent {
        public String reason;
        public long threadId;

        /**
         * Constructor.
         */
        public ThreadEvent(String reason, long threadId) {
            super("thread");
            this.reason = reason;
            this.threadId = threadId;
        }
    }

    public static class OutputEvent extends DebugEvent {
        public enum Category {
            console, stdout, stderr, telemetry
        }

        public Category category;
        public String output;
        public int variablesReference;
        public Source source;
        public int line;
        public int column;
        public Object data;

        /**
         * Constructor.
         */
        public OutputEvent(Category category, String output) {
            super("output");
            this.category = category;
            this.output = output;
        }

        /**
         * Constructor.
         */
        public OutputEvent(Category category, String output, Source source, int line) {
            super("output");
            this.category = category;
            this.output = output;
            this.source = source;
            this.line = line;
        }

        public static OutputEvent createConsoleOutput(String output) {
            return new OutputEvent(Category.console, output);
        }

        public static OutputEvent createStdoutOutput(String output) {
            return new OutputEvent(Category.stdout, output);
        }

        /**
         * Construct an stdout output event with source info.
         */
        public static OutputEvent createStdoutOutputWithSource(String output, Source source, int line) {
            return new OutputEvent(Category.stdout, output, source, line);
        }

        public static OutputEvent createStderrOutput(String output) {
            return new OutputEvent(Category.stderr, output);
        }

        /**
         * Construct an stderr output event with source info.
         */
        public static OutputEvent createStderrOutputWithSource(String output, Source source, int line) {
            return new OutputEvent(Category.stderr, output, source, line);
        }

        public static OutputEvent createTelemetryOutput(String output) {
            return new OutputEvent(Category.telemetry, output);
        }
    }

    public static class BreakpointEvent extends DebugEvent {
        public String reason;
        public Types.Breakpoint breakpoint;

        /**
         * Constructor.
         */
        public BreakpointEvent(String reason, Types.Breakpoint breakpoint) {
            super("breakpoint");
            this.reason = reason;
            this.breakpoint = breakpoint;
        }
    }

    public static class HotCodeReplaceEvent extends DebugEvent {
        public enum ChangeType {
            ERROR, WARNING, STARTING, END, BUILD_COMPLETE
        }

        public ChangeType changeType;
        public String message;

        /**
         * Constructor.
         */
        public HotCodeReplaceEvent(ChangeType changeType, String message) {
            super("hotcodereplace");
            this.changeType = changeType;
            this.message = message;
        }
    }

    public static class UserNotificationEvent extends DebugEvent {
        public enum NotificationType {
            ERROR, WARNING, INFORMATION
        }

        public NotificationType notificationType;
        public String message;

        /**
         * Constructor.
         */
        public UserNotificationEvent(NotificationType notifyType, String message) {
            super("usernotification");
            this.notificationType = notifyType;
            this.message = message;
        }
    }

    public static class TelemetryEvent extends DebugEvent {
        /**
         * The telemetry event name.
         */
        public String name;

        /**
         * The properties is an object as below.
         * {
         *   [key: string]: string | number;
         * }
         */
        public Object properties;

        /**
         * Constructor.
         */
        public TelemetryEvent(String name, Object data) {
            super("telemetry");
            this.name = name;
            this.properties = data;
        }
    }

    public static enum InvalidatedAreas {
        @SerializedName("all")
        ALL,
        @SerializedName("stacks")
        STACKS,
        @SerializedName("threads")
        THREADS,
        @SerializedName("variables")
        VARIABLES;
    }

    public static class InvalidatedEvent extends DebugEvent {
        public InvalidatedAreas[] areas;
        public long threadId;
        public int frameId;

        public InvalidatedEvent() {
            super("invalidated");
        }

        public InvalidatedEvent(InvalidatedAreas area) {
            super("invalidated");
            this.areas = new InvalidatedAreas[]{area};
        }

        public InvalidatedEvent(InvalidatedAreas area, long threadId) {
            super("invalidated");
            this.areas = new InvalidatedAreas[]{area};
            this.threadId = threadId;
        }

        public InvalidatedEvent(InvalidatedAreas area, int frameId) {
            super("invalidated");
            this.areas = new InvalidatedAreas[]{area};
            this.frameId = frameId;
        }
    }

    public static class ProcessIdNotification extends DebugEvent {
        /**
         * The process ID.
         */
        public long processId = -1;
        /**
         * The process ID of the terminal shell if the process is running in a terminal shell.
         */
        public long shellProcessId = -1;

        public ProcessIdNotification(long processId) {
            super("processid");
            this.processId = processId;
        }

        public ProcessIdNotification(long processId, long shellProcessId) {
            super("processid");
            this.processId = processId;
            this.shellProcessId = shellProcessId;
        }
    }
}
