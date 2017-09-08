package com.microsoft.java.debug.core.adapter;

public interface IVirtualMachineManagerProvider extends IProvider {
    com.sun.jdi.VirtualMachineManager getVirtualMachineManager();
}
