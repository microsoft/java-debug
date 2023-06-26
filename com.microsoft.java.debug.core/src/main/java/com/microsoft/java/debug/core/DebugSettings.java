/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Requests.ExceptionFilters;
import com.microsoft.java.debug.core.protocol.Requests.StepFilters;

public final class DebugSettings {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static Set<IDebugSettingChangeListener> listeners =
        Collections.newSetFromMap(new ConcurrentHashMap<IDebugSettingChangeListener, Boolean>());
    private static DebugSettings current = new DebugSettings();

    public int maxStringLength = 0;
    public int numericPrecision = 0;
    public boolean showStaticVariables = false;
    public boolean showQualifiedNames = false;
    public boolean showHex = false;
    public boolean showLogicalStructure = true;
    public boolean showToString = true;
    public String logLevel;
    public String javaHome;
    public HotCodeReplace hotCodeReplace = HotCodeReplace.MANUAL;
    public StepFilters stepFilters = new StepFilters();
    public ExceptionFilters exceptionFilters = new ExceptionFilters();
    public boolean exceptionFiltersUpdated = false;
    public int limitOfVariablesPerJdwpRequest = 100;
    public int jdwpRequestTimeout = 3000;
    public AsyncMode asyncJDWP = AsyncMode.OFF;
    public Switch debugSupportOnDecompiledSource = Switch.OFF;

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
            DebugSettings oldSettings = current;
            current = JsonUtils.fromJson(jsonSettings, DebugSettings.class);
            for (IDebugSettingChangeListener listener : listeners) {
                listener.update(oldSettings, current);
            }
        } catch (JsonSyntaxException ex) {
            logger.severe(String.format("Invalid json for debugSettings: %s, %s", jsonSettings, ex.getMessage()));
        }
    }

    private DebugSettings() {

    }

    public static boolean addDebugSettingChangeListener(IDebugSettingChangeListener listener) {
        return listeners.add(listener);
    }

    public static boolean removeDebugSettingChangeListener(IDebugSettingChangeListener listener) {
        return listeners.remove(listener);
    }

    public static enum HotCodeReplace {
        @SerializedName("manual")
        MANUAL,
        @SerializedName("auto")
        AUTO,
        @SerializedName("never")
        NEVER
    }

    public static enum AsyncMode {
        @SerializedName("auto")
        AUTO,
        @SerializedName("on")
        ON,
        @SerializedName("off")
        OFF
    }

    public static enum Switch {
        @SerializedName("on")
        ON,
        @SerializedName("off")
        OFF
    }

    public static interface IDebugSettingChangeListener {
        public void update(DebugSettings oldSettings, DebugSettings newSettings);
    }
}
