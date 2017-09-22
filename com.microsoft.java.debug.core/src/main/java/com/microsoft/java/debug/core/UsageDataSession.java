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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.microsoft.java.debug.core.adapter.Messages.Request;
import com.microsoft.java.debug.core.adapter.Messages.Response;

public class UsageDataSession {
    private static final Logger usageDataLogger = Logger.getLogger(Configuration.USAGE_DATA_LOGGER_NAME);
    private static final long RESPONSE_MAX_DELAY_MS = 1000;
    private long startAt = -1;
    private long stopAt = -1;
    private Map<String, Integer> commandCountMap = new HashMap<>();
    private Map<String, Integer> breakpointCountMap = new HashMap<>();
    private Map<Integer, RequestEvent> requestEventMap = new HashMap<>();

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
        requestEventMap.put(request.seq, new RequestEvent(request, System.currentTimeMillis()));

        // cmd count
        commandCountMap.put(request.command, commandCountMap.getOrDefault(request.command, 0) + 1);

        // bp count
        if ("setBreakpoints".equals(request.command)) {
            String filename = request.arguments.get("source").getAsJsonObject().get("path").getAsString();
            String filenameHash = "error in filename hashing";
            try {
                byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(filename.getBytes(StandardCharsets.UTF_8));
                StringBuffer buf = new StringBuffer();
                for (byte b : hashBytes) {
                    buf.append(Integer.toHexString((b & 0xFF) + 0x100).substring(1));
                }
                filenameHash = buf.toString();
            } catch (NoSuchAlgorithmException e) {
                // ignore it.
            }
            int bpCount = request.arguments.get("breakpoints").getAsJsonArray().size();
            breakpointCountMap.put(filenameHash, breakpointCountMap.getOrDefault(filenameHash, 0) + bpCount);
        }
    }

    /**
     * Record usage data from response.
     */
    public void recordResponse(Response response) {
        long responseMillis = System.currentTimeMillis();
        long requestMillis = responseMillis;
        String requestCommand = null;

        RequestEvent requestEvent = requestEventMap.getOrDefault(response.request_seq, null);
        if (requestEvent != null) {
            requestCommand = requestEvent.request.command;
            requestMillis = requestEvent.timestamp;
            requestEventMap.remove(response.request_seq);
        }
        long respondingTime = responseMillis - requestMillis;

        if (!response.success || respondingTime > RESPONSE_MAX_DELAY_MS) {
            Map<String, Object> props = new HashMap<>();
            props.put("respondingTime", respondingTime);
            props.put("requestCommand", requestCommand);
            props.put("success", response.success);
            // directly report abnormal response.
            usageDataLogger.log(Level.WARNING, "abnormal response", props);
        }
    }

    /**
     * Submit summary of usage data in current session.
     */
    public void submitUsageData() {
        Map<String, String> props = new HashMap<>();

        props.put("sessionStartAt", String.valueOf(startAt));
        props.put("sessionStopAt", String.valueOf(stopAt));
        props.put("commandCount", new Gson().toJson(commandCountMap));
        props.put("breakpointCount", new Gson().toJson(breakpointCountMap));
        usageDataLogger.log(Level.INFO, "session usage data summary", props);
    }

}
