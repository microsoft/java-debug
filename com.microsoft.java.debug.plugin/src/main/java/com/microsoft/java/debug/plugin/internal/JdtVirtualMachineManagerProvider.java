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

import com.microsoft.java.debug.core.adapter.IVirtualMachineManager;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.sun.jdi.VirtualMachineManager;

public class JdtVirtualMachineManagerProvider implements IVirtualMachineManagerProvider {
    private static IVirtualMachineManager vmManager = null;

    @Override
    public synchronized VirtualMachineManager getVirtualMachineManager() {
        if (vmManager == null) {
            vmManager = new AdvancedVirtualMachineManager();
        }
        return vmManager;
    }
}
