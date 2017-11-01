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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.microsoft.java.debug.core.Log;

public class JavaDebuggerServerPlugin implements BundleActivator {
    public static final String PLUGIN_ID = "com.microsoft.java.debug";
    public static BundleContext context = null;

    @Override
    public void start(BundleContext context) throws Exception {
        JavaDebuggerServerPlugin.context = context;
        LogUtils.initialize(Level.INFO);
        Log.info("Starting %s", PLUGIN_ID);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        Log.info("Stopping %s", PLUGIN_ID);
    }

}

