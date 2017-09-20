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

package com.microsoft.java.debug.core.adapter;

import com.microsoft.java.debug.core.AbstractJdiTestCase;
import com.microsoft.java.debug.core.DebugSessionFactory;
import com.microsoft.java.debug.core.IDebugSession;
import org.junit.AfterClass;
import org.junit.Before;

import static com.microsoft.java.debug.core.DebugSessionFactory.shutdownDebugSession;

public abstract class BaseJdiTestCase extends AbstractJdiTestCase {
    protected static final String PROJECT_NAME = "4.variable";
    protected IDebugSession debugSession;

    @AfterClass
    public static void tearDownClass() throws Exception {
        staticBreakpointEvent = null;
        shutdownDebugSession(PROJECT_NAME);
    }

    @Before
    public void setup() throws Exception {
        debugSession = DebugSessionFactory.getDebugSession(PROJECT_NAME, "VariableTest");
        if (staticBreakpointEvent == null) {
            staticBreakpointEvent = waitForBreakPointEvent("VariableTest", 60);
        }
    }

    @Override
    protected IDebugSession getCurrentDebugSession() {
        return debugSession;
    }
}
