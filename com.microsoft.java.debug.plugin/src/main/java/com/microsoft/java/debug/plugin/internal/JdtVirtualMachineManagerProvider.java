package com.microsoft.java.debug.plugin.internal;

import java.util.Map;

import org.eclipse.jdi.Bootstrap;

import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.sun.jdi.VirtualMachineManager;

public class JdtVirtualMachineManagerProvider implements IVirtualMachineManagerProvider {

    @Override
    public VirtualMachineManager getVirtualMachineManager() {
        return Bootstrap.virtualMachineManager();
    }

    @Override
    public void initialize(Map<String, Object> props) {
    }
}
