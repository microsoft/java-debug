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

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.UserSettings;

public class UserSettingsUtils {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    /**
     * Configure user setting for java debugger.
     *
     * @param arguments
     *            the arguments for the settings, eg: show_hex=true show_static_variables=true show_qualified_names=true
     *            max-string-length=100 --max-string-length=200
     */
    public static Object configUserSettings(List<Object> arguments) {
        if (arguments != null && arguments.size() > 0) {
            arguments.forEach(arg -> {
                if (arg instanceof String) {
                    String keyStr = (String) arg;
                    String[] keyValue = StringUtils.split(keyStr, "=", 2);
                    if (keyValue.length != 2) {
                        logger.warning(String.format("Invalid parameter for userSettings: %s", keyStr));
                        return;
                    }

                    keyStr = StringUtils.trim(keyValue[0]);
                    String valueStr = StringUtils.trim(keyValue[1]);

                    try {
                        switch (keyStr) {
                            case "show_hex":
                                UserSettings.showHex = Boolean.parseBoolean(valueStr);
                                break;
                            case "show_static_variables":
                                UserSettings.showStaticVariables = Boolean.parseBoolean(valueStr);
                                break;
                            case "show_qualified_names":
                                UserSettings.showQualifiedNames = Boolean.parseBoolean(valueStr);
                                break;
                            case "max_string_length":
                                UserSettings.maxStringLength = Integer.parseInt(valueStr);
                                break;
                            case "max_string_length_console":
                                UserSettings.maxStringLengthConsole = Integer.parseInt(valueStr);
                                break;
                            default:
                                logger.warning(String.format("Invalid parameter for userSettings: %s", keyStr));
                        }
                    } catch (NumberFormatException ex) {
                        logger.severe(String.format("Invalid parameters for userSettings: %s", arg));
                    }

                } else {
                    logger.severe(String.format("Parameters for userSettings must be string: %s", String.valueOf(arg)));
                }
            });

        } else {
            logger.severe(String.format("Invalid parameters for userSettings: %s", StringUtils.join(arguments)));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        if (UserSettings.showHex) {
            sb.append("show_hex ON\n");
        }

        if (UserSettings.showQualifiedNames) {
            sb.append("show_qualified_names ON\n");
        }

        if (UserSettings.showStaticVariables) {
            sb.append("show_static_variables ON\n");
        }

        if (UserSettings.maxStringLength > 0) {
            sb.append(String.format("max_string_length %d\n", UserSettings.maxStringLength));
        }

        if (UserSettings.maxStringLengthConsole > 0) {
            sb.append(String.format("max_string_length_console %d\n", UserSettings.maxStringLengthConsole));
        }

        sb.append("}\n");
        return sb.toString();
    }
}
