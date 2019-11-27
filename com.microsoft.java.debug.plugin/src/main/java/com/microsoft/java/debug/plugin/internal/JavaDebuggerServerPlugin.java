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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.microsoft.java.debug.core.Configuration;

public class JavaDebuggerServerPlugin implements BundleActivator {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    public static final String PLUGIN_ID = "com.microsoft.java.debug.plugin";
    public static BundleContext context = null;

    @Override
    public void start(BundleContext context) throws Exception {
        JavaDebuggerServerPlugin.context = context;
        LogUtils.initialize(Level.INFO);
        logger.info("Starting " + PLUGIN_ID);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        logger.info("Stopping " + PLUGIN_ID);
        LogUtils.cleanupHandlers();
    }

}

