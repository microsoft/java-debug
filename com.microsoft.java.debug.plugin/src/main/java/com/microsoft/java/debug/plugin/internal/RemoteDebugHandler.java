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

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.IProviderContext;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.LRUCache;
import com.microsoft.java.debug.core.adapter.ProviderContext;


public class RemoteDebugHandler {
    private IProviderContext context = new ProviderContext();
    private Map<String, String> sourceMappingCache = Collections.synchronizedMap(new LRUCache<>(10000));

    /**
     * Remote debug hander.
     *
     * @param projectName the project name
     * @param charset the encoding specified by launch.json, if it is null, the default encoding will be used.
     */
    public RemoteDebugHandler(String projectName, Charset charset) {
        if (projectName == null) {
            throw new IllegalArgumentException("projectName is required for remote debugging.");
        }
        Map<String, Object> options = new HashMap<>();
        options.put(Constants.DEBUGGEE_ENCODING, charset);
        options.put(Constants.PROJECT_NAME, projectName);
        JdtSourceLookUpProvider jdtSourceLookUpProvider = new JdtSourceLookUpProvider();
        jdtSourceLookUpProvider.initialize(null, options);
        context.registerProvider(ISourceLookUpProvider.class, jdtSourceLookUpProvider);
    }

    /**
     * Get the fully qualified names of give java file locations.
     *
     * @param file the java file
     * @param lines the lines to on the file
     * @return the fully qualified names
     */
    public String[] resolveClassName(String file, int[] lines, int[] columns) {
        String sourcePath = AdapterUtils.convertPath(file, AdapterUtils.isUri(file), true);
        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);

        try {
            return sourceProvider.getFullyQualifiedName(sourcePath, lines, null);
        } catch (DebugException e) {
            return new String[0];
        }
    }

    /**
     * Given a class with a fully qualified name, resolve the physical disk location
     * of the source file of the class.
     *
     * @param fullyQualifiedName
     *            the class name
     * @param sourcePath
     *            the source path uri for class
     * @return the souce file path
     */
    public String resolveSourceName(String fullyQualifiedName, String sourcePath) {
        String uri = sourceMappingCache.computeIfAbsent(fullyQualifiedName, key -> {
            ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
            String fromProvider = sourceProvider.getSourceFileURI(key, sourcePath);
            // avoid return null which will cause the compute function executed again
            return StringUtils.isBlank(fromProvider) ? "" : fromProvider;
        });
        if (!StringUtils.isBlank(uri)) {
            // The Source.path could be a file system path or uri string.
            if (uri.startsWith("file:")) {
                return AdapterUtils.convertPath(uri, true, true);
            } else {
                // If the debugger returns uri in the Source.path for the StackTrace response,
                // VSCode client will try to find a TextDocumentContentProvider
                // to render the contents.
                // Language Support for Java by Red Hat extension has already registered a jdt
                // TextDocumentContentProvider to parse the jdt-based uri.
                // The jdt uri looks like
                // 'jdt://contents/rt.jar/java.io/PrintStream.class?=1.helloworld/%5C/usr%5C/lib%5C/jvm%5C/java-8-oracle%5C/jre%5C/
                // lib%5C/rt.jar%3Cjava.io(PrintStream.class'.
                return uri;
            }
        }
        return "";
    }
}
