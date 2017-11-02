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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdi.internal.VirtualMachineManagerImpl;

import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.LaunchingConnector;

public class AdvancedVirtualMachineManager extends VirtualMachineManagerImpl implements VirtualMachineManager {

    @Override
    public List<LaunchingConnector> launchingConnectors() {
        List<LaunchingConnector> connectors = new ArrayList<>();
        connectors.add(new AdvancedLaunchingConnector(this));
        connectors.addAll(super.launchingConnectors());
        return connectors;
    }

}
