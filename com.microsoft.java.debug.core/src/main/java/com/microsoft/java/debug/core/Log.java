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

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.adapter.Messages;
import com.sun.jdi.event.Event;

/**
 * This helper class provides static methods for a default instance of <code>java.util.logging.Logger</code>,a simple code like
 * <code>Log.info("Sample trace: %d", 10)</code> is able to replace <code>logger.info(String.format("Sample trace: %d", 10))</code>.
 * Using a very short name to better code fitness.
 */
public final class Log {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    /**
     * Log an ERROR message.
     *
     * @param ex The exception object
     * @param message The string message
     * @param arguments Arguments referenced by the format specifiers in the message
     */
    public static void error(Exception ex, String message, Object... arguments) {
        logger.log(Level.SEVERE, String.format(message, arguments), ex);
    }


    /**
     * Log an ERROR message.
     *
     * @param message The string message
     * @param arguments Arguments referenced by the format specifiers in the message
     */
    public static void error(String message, Object... arguments) {
        logger.severe(String.format(message, arguments));
    }

    /**
     * Log a INFO message.
     *
     * @param message The string message
     * @param arguments Arguments referenced by the format specifiers in the message
     *         string.
     */
    public static void info(String message, Object... arguments) {
        logger.info(String.format(message, arguments));
    }

    /**
     * Log a WARN message.
     *
     * @param message The string message
     * @param arguments Arguments referenced by the format specifiers in the message
     */
    public static void warn(String message, Object... arguments) {
        logger.warning(String.format(message, arguments));
    }

    /**
     * Log a DEBUG message.
     *
     * @param message The string message
     * @param arguments Arguments referenced by the format specifiers in the message
     */
    public static void debug(String message, Object... arguments) {
        logger.fine(String.format(message, arguments));
    }


    /**
     * Set the log level specifying which message levels will be
     * logged by this logger.  Message levels lower than this
     * value will be discarded.  The level value Level.OFF
     * can be used to turn off logging.
     *
     * @param newLevel   the new value for the log level (may be null)
     */
    public static void setLevel(Level newLevel) {
        logger.setLevel(newLevel);
    }

    /**
     * Add a log Handler to receive logging messages.
     * @param   handler a logging Handler
     */
    public static void addHandler(Handler handler) {
        logger.addHandler(handler);
    }

    /**
     * Remove a log Handler.
     * Returns silently if the given Handler is not found or is null
     *
     * @param   handler a logging Handler
     */
    public static void removeHandler(Handler handler) throws SecurityException {
        logger.removeHandler(handler);
    }

    /**
     * Record a JDI event.
     * @param event the JDI event.
     */
    public static void traceEvent(Event event) {
        UsageDataSession.recordEvent(event);
    }


    /**
     * Begin an user data collection session to record events, requests and errors.
     *
     * @return a new user data collection session
     */
    public static UsageDataSession beginSession() {
        UsageDataSession session = new UsageDataSession();
        session.reportStart();
        return session;
    }

    /**
     * Record the request from VSCode side.
     *
     * @param session user data collection session
     * @param request from VSCode.
     */
    public static void traceRequest(UsageDataSession session, Messages.Request request) {
        if (session != null && request != null) {
            session.recordRequest(request);
        }
    }

    /**
     * Record the response sends to VSCode.
     *
     * @param session user data collection session
     * @param response to VSCode.
     */
    public static void traceResponse(UsageDataSession session, Messages.Response response) {
        if (session != null && response != null) {
            session.recordResponse(response);
        }
    }

    /**
     * End an user data collection session, and report the usage datas.
     *
     * @param session user data collection session
     */
    public static void endSession(UsageDataSession session) {
        if (session != null) {
            session.reportStop();
            session.submitUsageData();
        }
    }


    private Log() {

    }
}
