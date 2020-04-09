/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;

public class PlatformSettings {

    /**
     * Resolve the JDT platform settings.
     */
    public static Map<String, String> getPlatformSettings() {
        Map<String, String> result = new HashMap<>();
        result.put("latestSupportedJavaVersion", JavaCore.latestSupportedJavaVersion());
        return result;
    }
}
