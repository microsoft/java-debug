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
}
