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

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class ResolveSourceMappingHandler {
    private static final Pattern SOURCE_PATTERN = Pattern.compile("([\\w$\\.]+\\/)?(([\\w$]+\\.)+[<\\w$>]+)\\(([\\w-$]+\\.java:\\d+)\\)");
    private static final JdtSourceLookUpProvider sourceProvider = new JdtSourceLookUpProvider();

    public static String resolveSourceUri(List<Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }

        return resolveSourceUri((String) arguments.get(0));
    }

    public static String resolveSourceUri(String lineText) {
        if (lineText == null) {
            return null;
        }

        Matcher matcher = SOURCE_PATTERN.matcher(lineText);
        if (matcher.find()) {
            String methodField = matcher.group(2);
            String locationField = matcher.group(matcher.groupCount());
            String fullyQualifiedName = methodField.substring(0, methodField.lastIndexOf("."));
            String packageName = fullyQualifiedName.lastIndexOf(".") > -1 ? fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf(".")) : "";
            String[] locations = locationField.split(":");
            String sourceName = locations[0];
            String sourcePath = StringUtils.isBlank(packageName) ? sourceName
                    : packageName.replace('.', File.separatorChar) + File.separatorChar + sourceName;
            return sourceProvider.getSourceFileURI(fullyQualifiedName, sourcePath);
        }

        return null;
    }
}
