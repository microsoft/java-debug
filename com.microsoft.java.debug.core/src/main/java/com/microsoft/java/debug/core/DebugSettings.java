/*******************************************************************************
 * Copyright (c) 2017-2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core;

import java.util.logging.Logger;

import com.google.gson.JsonSyntaxException;
import com.microsoft.java.debug.core.protocol.JsonUtils;

public final class DebugSettings {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static DebugSettings current = new DebugSettings();

    public int maxStringLength = 0;
    public boolean showStaticVariables = true;
    public boolean showQualifiedNames = false;
    public boolean showHex = false;
    public boolean enableHotCodeReplace = false;
    public boolean showLogicalStructure = true;
    public String logLevel;
    public String javaHome;

    public static DebugSettings getCurrent() {
        return current;
    }

    /**
     * Update current settings with the values in the parameter.
     *
     * @param jsonSettings
     *            the new settings represents in json format.
     */
    public void updateSettings(String jsonSettings) {
        try {
            current = JsonUtils.fromJson(jsonSettings, DebugSettings.class);
        } catch (JsonSyntaxException ex) {
            logger.severe(String.format("Invalid json for debugSettings: %s, %s", jsonSettings, ex.getMessage()));
        }
    }

    private DebugSettings() {

    }
}
