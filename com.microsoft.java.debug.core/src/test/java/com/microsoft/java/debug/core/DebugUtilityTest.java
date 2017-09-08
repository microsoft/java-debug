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

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.easymock.EasyMock;
import org.easymock.EasyMockRule;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;

public class DebugUtilityTest extends EasyMockSupport {
    @Rule
    public EasyMockRule rule = new EasyMockRule(this);

    @Mock
    private LaunchingConnector mockConnector;

    @Mock
    private VirtualMachineManager mockVMManager;

    @Mock
    private Argument mockOptions;

    @Mock
    private Argument mockSuspend;

    @Mock
    private Argument mockMainClass;

    @Mock
    private VirtualMachine mockVM;

    @Before
    public void setup() {
    }

    @Test
    public void testLaunch()
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        List<LaunchingConnector> connectors = new ArrayList<LaunchingConnector>();
        connectors.add(mockConnector);
        Map<String, Argument> defaultArgumentsMap = new HashMap<String, Argument>();
        defaultArgumentsMap.put("options", mockOptions);
        defaultArgumentsMap.put("suspend", mockSuspend);
        defaultArgumentsMap.put("main", mockMainClass);

        EasyMock.expect(mockVMManager.launchingConnectors()).andReturn(connectors);
        EasyMock.expect(mockConnector.defaultArguments()).andReturn(defaultArgumentsMap);

        mockOptions.setValue("-cp \"c:/foo\"");
        mockMainClass.setValue("foo.Bar");
        mockSuspend.setValue("true");
        EasyMock.expect(mockConnector.launch(defaultArgumentsMap)).andReturn(mockVM);
        replayAll();
        IDebugSession debugSession = DebugUtility.launch(mockVMManager, "foo.Bar", "", "", "c:/foo");
        assertNotNull(debugSession);
        verifyAll();
    }
}
