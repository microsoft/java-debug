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

import com.google.gson.JsonObject;

/**
 * The response types defined by VSCode Debug Protocol.
 */
public class Messages {

    public static class ProtocolMessage {
        public int seq;
        public String type;

        public ProtocolMessage(String type) {
            this.type = type;
        }
    }

    public static class Request extends ProtocolMessage {
        public String command;
        public JsonObject arguments;

        /**
         * Constructor.
         */
        public Request(int id, String cmd, JsonObject arg) {
            super("request");
            this.seq = id;
            this.command = cmd;
            this.arguments = arg;
        }

        /**
        * Constructor.
        */
        public Request(String cmd, JsonObject arg) {
            super("request");
            this.command = cmd;
            this.arguments = arg;
        }
    }

    public static class Response extends ProtocolMessage {
        public boolean success;
        public String message;
        public int request_seq;
        public String command;
        public Object body;

        public Response() {
            super("response");
        }

        /**
         * Constructor.
         */
        public Response(String message) {
            super("response");
            this.success = false;
            this.message = message;
        }

        /**
         * Constructor.
         */
        public Response(boolean success, String message) {
            super("response");
            this.success = success;
            this.message = message;
        }

        /**
         * Constructor.
         */
        public Response(Response response) {
            super("response");
            this.seq = response.seq;
            this.success = response.success;
            this.message = response.message;
            this.request_seq = response.request_seq;
            this.command = response.command;
            this.body = response.body;
        }

        /**
         * Constructor.
         */
        public Response(int requestSeq, String command) {
            super("response");
            this.request_seq = requestSeq;
            this.command = command;
        }

        public Response(int requestSeq, String command, boolean success) {
            this(requestSeq, command);
            this.success = success;
        }

        /**
         * Constructor.
         */
        public Response(int requestSeq, String command, boolean success, String message) {
            this(requestSeq, command);
            this.success = success;
            this.message = message;
        }
    }

    public static class Event extends ProtocolMessage {
        public String event;
        public Object body;

        public Event() {
            super("event");
        }

        /**
         * Constructor.
         */
        public Event(Event m) {
            super("event");
            this.seq = m.seq;
            this.event = m.event;
            this.body = m.body;
        }

        /**
         * Constructor.
         */
        public Event(String type, Object body) {
            super("event");
            this.event = type;
            this.body = body;
        }
    }
}
