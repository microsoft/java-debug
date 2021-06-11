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

package com.microsoft.java.debug.plugin.internal;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;

public final class LogUtils {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final Logger usageDataLogger = Logger.getLogger(Configuration.USAGE_DATA_LOGGER_NAME);

    /**
     * Initialize logger for logger level and logger handler.
     * @param level the logger level for java debugger.
     */
    public static void initialize(Level level) {
        logger.addHandler(new JdtLogHandler());
        logger.addHandler(new UsageDataLogHandler(Level.SEVERE));
        usageDataLogger.addHandler(new UsageDataLogHandler(Level.ALL));
        logger.setLevel(level);
    }

    /**
     * Remove the logger handlers registered to the global logger.
     */
    public static void cleanupHandlers() {
        Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            logger.removeHandler(handler);
        }

        Handler[] usageHandlers = usageDataLogger.getHandlers();
        for (Handler handler : usageHandlers) {
            usageDataLogger.removeHandler(handler);
        }
    }

    /**
     * Configure log level setting for java debugger.
     *
     * @param logLevel
     *            the String representation of level(info, fine, warning..).
     * @return the updated log level
     */
    public static String configLogLevel(Object logLevel) {
        try {
            logger.setLevel(Level.parse((String) logLevel));
            logger.info(String.format("Set log level to : %s", logLevel));
        } catch (IllegalArgumentException e) {
            logger.severe(String.format("Invalid log level: %s", logLevel));
        }  catch (ClassCastException e) {
            logger.severe("logLevel should be a string.");
        }

        return logger.getLevel().toString();
    }

}
