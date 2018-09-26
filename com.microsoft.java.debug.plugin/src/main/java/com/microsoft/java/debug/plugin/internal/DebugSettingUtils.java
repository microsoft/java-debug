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

import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonSyntaxException;
import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.protocol.JsonUtils;

public final class DebugSettingUtils {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    /**
     * Configure setting for java debugger.
     *
     * @param arguments
     *            the arguments for the settings, the format is json,
     *            eg:{"showHex":true,"showStaticVariables":true,"maxStringLength":100}
     */
    public static Object configDebugSettings(List<Object> arguments) {
        if (arguments != null && arguments.size() > 0) {
            arguments.forEach(arg -> {
                if (arg instanceof String) {
                    String jsonStr = (String) arg;

                    try {
                        DebugSettings.getCurrent().updateSettings(jsonStr);
                        DebugSettings.getCurrent().logLevel = LogUtils.configLogLevel(DebugSettings.getCurrent().logLevel);
                    } catch (JsonSyntaxException ex) {
                        logger.severe(String.format("Parameters for userSettings must be a valid json: %s", String.valueOf(arg)));
                    }

                } else {
                    logger.severe(String.format("Parameters for userSettings must be json string: %s", String.valueOf(arg)));
                }
            });

        } else {
            logger.severe(String.format("Invalid parameters for debugSettings: %s", StringUtils.join(arguments.toArray(), ',')));
        }
        return JsonUtils.toJson(DebugSettings.getCurrent());
    }

    /**
     * Private constructor to prevent creating instance of this class.
     */
    private DebugSettingUtils() {

    }
}
