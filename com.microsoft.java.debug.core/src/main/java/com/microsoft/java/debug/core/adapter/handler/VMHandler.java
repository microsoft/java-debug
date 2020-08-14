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

package com.microsoft.java.debug.core.adapter.handler;

import com.microsoft.java.debug.core.adapter.IVirtualMachineManager;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;

public class VMHandler {
    private IVirtualMachineManagerProvider vmProvider = null;

    public VMHandler() {
    }

    public VMHandler(IVirtualMachineManagerProvider vmProvider) {
        this.vmProvider = vmProvider;
    }

    public IVirtualMachineManagerProvider getVmProvider() {
        return vmProvider;
    }

    public void setVmProvider(IVirtualMachineManagerProvider vmProvider) {
        this.vmProvider = vmProvider;
    }

    public void connectVirtualMachine(VirtualMachine vm) {
        if (vm != null && vmProvider != null) {
            VirtualMachineManager vmManager = vmProvider.getVirtualMachineManager();
            if (vmManager instanceof IVirtualMachineManager) {
                ((IVirtualMachineManager) vmManager).connectVirtualMachine(vm);
            }
        }
    }

    public void disconnectVirtualMachine(VirtualMachine vm) {
        if (vm != null && vmProvider != null) {
            VirtualMachineManager vmManager = vmProvider.getVirtualMachineManager();
            if (vmManager instanceof IVirtualMachineManager) {
                ((IVirtualMachineManager) vmManager).disconnectVirtualMachine(vm);
            }
        }
    }
}
