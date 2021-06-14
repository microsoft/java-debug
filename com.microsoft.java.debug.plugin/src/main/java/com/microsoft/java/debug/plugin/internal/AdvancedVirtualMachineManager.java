/*******************************************************************************
 * Copyright (c) 2017-2020 Microsoft Corporation and others.
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
import java.util.Collections;
import java.util.List;

import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.DebugSettings.IDebugSettingChangeListener;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManager;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.LaunchingConnector;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdi.internal.VirtualMachineManagerImpl;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.core.JDIDebugPlugin;

public class AdvancedVirtualMachineManager extends VirtualMachineManagerImpl
        implements VirtualMachineManager, IDebugSettingChangeListener, IVirtualMachineManager {
    List<VirtualMachine> connectedVMs = Collections.synchronizedList(new ArrayList());

    public AdvancedVirtualMachineManager() {
        super();
        update(DebugSettings.getCurrent(), DebugSettings.getCurrent());
        DebugSettings.addDebugSettingChangeListener(this);
    }

    @Override
    public List<LaunchingConnector> launchingConnectors() {
        List<LaunchingConnector> connectors = new ArrayList<>();
        connectors.add(new AdvancedLaunchingConnector(this));
        connectors.addAll(super.launchingConnectors());
        return connectors;
    }

    @Override
    public void update(DebugSettings oldSettings, DebugSettings newSettings) {
        int currentTimeout = getGlobalRequestTimeout();
        int newTimeout = newSettings.jdwpRequestTimeout;
        if (newTimeout != currentTimeout) {
            setRequestTimeout(newTimeout);
        }
    }

    private void setRequestTimeout(int timeout) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(JDIDebugPlugin.getUniqueIdentifier());
        if (prefs != null) {
            prefs.putInt(JDIDebugModel.PREF_REQUEST_TIMEOUT, timeout);
        }

        if (!connectedVMs.isEmpty()) {
            connectedVMs.forEach(vm -> {
                if (vm instanceof org.eclipse.jdi.VirtualMachine) {
                    try {
                        ((org.eclipse.jdi.VirtualMachine) vm).setRequestTimeout(timeout);
                    } catch (Exception e) {
                        // do nothing.
                    }
                }
            });
        }
    }

    @Override
    public boolean connectVirtualMachine(VirtualMachine vm) {
        return connectedVMs.add(vm);
    }

    @Override
    public boolean disconnectVirtualMachine(VirtualMachine vm) {
        return connectedVMs.remove(vm);
    }
}
