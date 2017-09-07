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

package com.microsoft.java.debug.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class JavaDebuggerServerPlugin implements BundleActivator {

    public static final String PLUGIN_ID = "com.microsoft.java.debug";


    @Override
    public void start(BundleContext context) throws Exception {
    	System.out.println("Starting " + PLUGIN_ID);
    }

    @Override
    public void stop(BundleContext context) throws Exception {

    }

}

