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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.protocol.JsonUtils;

public final class DebugSettings {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    public static int maxStringLength = 0;
    public static boolean showStaticVariables = true;
    public static boolean showQualifiedNames = false;
    public static boolean showHex = false;

    /**
     * Configure setting for java debugger.
     *
     * @param arguments
     *            the arguments for the settings, the format is json, eg:{"showHex":true,"showStaticVariables":true,"maxStringLength":100}
     */
    public static Object configDebugSettings(List<Object> arguments) {
        if (arguments != null && arguments.size() > 0) {
            arguments.forEach(arg -> {
                if (arg instanceof String) {
                    String jsonStr = (String) arg;

                    try {
                        Map<String, Object> map = JsonUtils.fromJson(jsonStr, new HashMap<String, Object>().getClass());

                        for (String keyStr : map.keySet()) {
                            Object valueObj = map.get(keyStr);
                            try {
                                switch (keyStr) {
                                    case "showHex":
                                        DebugSettings.showHex = (Boolean) valueObj;
                                        break;
                                    case "showStaticVariables":
                                        DebugSettings.showStaticVariables = (Boolean) valueObj;
                                        break;
                                    case "showQualifiedNames":
                                        DebugSettings.showQualifiedNames = (Boolean) valueObj;
                                        break;
                                    case "maxStringLength":
                                        DebugSettings.maxStringLength = ((Number) valueObj).intValue();
                                        break;
                                    default:
                                        logger.warning(String.format("Invalid parameter for debugSettings: %s", keyStr));
                                }
                            } catch (ClassCastException ex) {
                                logger.severe(String.format("Invalid parameters for debugSettings: %s - $s", jsonStr, keyStr));
                            }
                        }
                    } catch (com.google.gson.JsonSyntaxException ex) {
                        logger.severe(String.format("Parameters for userSettings must be a valid json: %s", String.valueOf(arg)));
                    }

                } else {
                    logger.severe(String.format("Parameters for userSettings must be json string: %s", String.valueOf(arg)));
                }
            });

        } else {
            logger.severe(String.format("Invalid parameters for debugSettings: %s", StringUtils.join(arguments)));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        if (DebugSettings.showHex) {
            sb.append("showHex ON\n");
        }

        if (DebugSettings.showQualifiedNames) {
            sb.append("showQualifiedNames ON\n");
        }

        if (DebugSettings.showStaticVariables) {
            sb.append("showStaticVariables ON\n");
        }

        if (DebugSettings.maxStringLength > 0) {
            sb.append(String.format("maxStringLength %d\n", DebugSettings.maxStringLength));
        }

        sb.append("}\n");
        return sb.toString();
    }
}
