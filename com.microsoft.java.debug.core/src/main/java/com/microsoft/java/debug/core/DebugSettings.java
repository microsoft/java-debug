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

import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.reflect.FieldUtils;

public final class DebugSettings {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static DebugSettings current = new DebugSettings();

    public int maxStringLength = 0;
    public boolean showStaticVariables = true;
    public boolean showQualifiedNames = false;
    public boolean showHex = false;
    public String logLevel;

    public static DebugSettings getCurrent() {
        return current;
    }

    /**
     * Update current settings with the values in the parameter.
     * @param newSettings the new settings.
     */
    public void updateSettings(Map<String, Object> newSettings) {
        for (String keyStr : newSettings.keySet()) {
            Object valueObj = newSettings.get(keyStr);
            if (valueObj instanceof Number) {
                valueObj = ((Number) valueObj).intValue();
            }
            try {
                FieldUtils.writeDeclaredField(current, keyStr, valueObj);
            } catch (IllegalArgumentException ex) {
                logger.severe(String.format("Invalid parameters for debugSettings: %s - %s, %s", keyStr, valueObj, ex.getMessage()));
            } catch (IllegalAccessException e) {
                logger.severe(String.format("Cannot write to debugSettings: %s - %s, %s", keyStr, valueObj, e.getMessage()));
            }
        }
    }
}
