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

package com.microsoft.java.debug.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonElement;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Messages.Request;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.sun.jdi.event.Event;

public class UsageDataSession {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final Logger usageDataLogger = Logger.getLogger(Configuration.USAGE_DATA_LOGGER_NAME);
    private static final long RESPONSE_MAX_DELAY_MS = 1000;
    private static final ThreadLocal<UsageDataSession> threadLocal = new InheritableThreadLocal<>();

    private final String sessionGuid = UUID.randomUUID().toString();
    private boolean jdiEventSequenceEnabled = false;
    private long startAt = -1;
    private long stopAt = -1;
    private Map<String, Integer> commandCountMap = new HashMap<>();
    private Map<String, Integer> breakpointCountMap = new HashMap<>();
    private Map<Integer, RequestEvent> requestEventMap = new HashMap<>();
    private List<String> eventList = new ArrayList<>();

    public static String getSessionGuid() {
        return threadLocal.get() == null ? "" : threadLocal.get().sessionGuid;
    }

    public UsageDataSession() {
        threadLocal.set(this);
    }

    class RequestEvent {
        Request request;
        long timestamp;

        RequestEvent(Request request, long timestamp) {
            this.request = request;
            this.timestamp = timestamp;
        }
    }

    public void reportStart() {
        startAt = System.currentTimeMillis();
    }

    public void reportStop() {
        stopAt = System.currentTimeMillis();
    }

    /**
     * Record usage data from request.
     */
    public void recordRequest(Request request) {
        try {
            requestEventMap.put(request.seq, new RequestEvent(request, System.currentTimeMillis()));

            // cmd count
            commandCountMap.put(request.command, commandCountMap.getOrDefault(request.command, 0) + 1);

            // bp count
            if ("setBreakpoints".equals(request.command)) {
                String fileIdentifier = "unknown file";
                JsonElement pathElement = request.arguments.get("source").getAsJsonObject().get("path");
                JsonElement nameElement = request.arguments.get("source").getAsJsonObject().get("name");
                if (pathElement != null) {
                    fileIdentifier = pathElement.getAsString();
                } else if (nameElement != null) {
                    fileIdentifier = nameElement.getAsString();
                }
                String filenameHash = AdapterUtils.getSHA256HexDigest(fileIdentifier);
                int bpCount = request.arguments.get("breakpoints").getAsJsonArray().size();
                breakpointCountMap.put(filenameHash, breakpointCountMap.getOrDefault(filenameHash, 0) + bpCount);
            }
        } catch (Throwable e) {
            // ignore it
        }
    }

    /**
     * Record usage data from response.
     */
    public void recordResponse(Response response) {
        try {
            long responseMillis = System.currentTimeMillis();
            long requestMillis = responseMillis;
            String command = null;

            RequestEvent requestEvent = requestEventMap.getOrDefault(response.request_seq, null);
            if (requestEvent != null) {
                command = requestEvent.request.command;
                requestMillis = requestEvent.timestamp;
                requestEventMap.remove(response.request_seq);
            }
            long duration = responseMillis - requestMillis;

            if (!response.success || duration > RESPONSE_MAX_DELAY_MS) {
                Map<String, Object> props = new HashMap<>();
                props.put("duration", duration);
                props.put("command", command);
                props.put("success", response.success);
                // directly report abnormal response.
                usageDataLogger.log(Level.WARNING, "abnormal response", props);
                jdiEventSequenceEnabled = true;
            }
        } catch (Throwable e) {
            // ignore it
        }
    }

    /**
     * Submit summary of usage data in current session.
     */
    public void submitUsageData() {
        Map<String, String> props = new HashMap<>();
        props.put("sessionStartAt", String.valueOf(startAt));
        props.put("sessionStopAt", String.valueOf(stopAt));
        props.put("commandCount", JsonUtils.toJson(commandCountMap));
        props.put("breakpointCount", JsonUtils.toJson(breakpointCountMap));
        if (jdiEventSequenceEnabled) {
            synchronized (eventList) {
                props.put("jdiEventSequence", JsonUtils.toJson(eventList));
            }
        }
        usageDataLogger.log(Level.INFO, "session usage data summary", props);
    }

    /**
     * Record JDI event.
     */
    public static void recordEvent(Event event) {
        try {
            UsageDataSession currentSession = threadLocal.get();
            if (currentSession != null) {
                Map<String, String> eventEntry = new HashMap<>();
                eventEntry.put("timestamp", String.valueOf(System.currentTimeMillis()));
                eventEntry.put("event", event.toString());
                synchronized (currentSession.eventList) {
                    currentSession.eventList.add(JsonUtils.toJson(eventEntry));
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("Exception on recording event: %s.", e.toString()), e);
        }
    }

    /**
     * Enable JDI event sequence track in current session.
     */
    public static void enableJdiEventSequence() {
        try {
            UsageDataSession currentSession = threadLocal.get();
            if (currentSession != null) {
                currentSession.jdiEventSequenceEnabled = true;
            }
        } catch (Exception e) {
            // ignore it
        }
    }
}
