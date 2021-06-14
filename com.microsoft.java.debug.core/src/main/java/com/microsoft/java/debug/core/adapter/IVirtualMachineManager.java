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

package com.microsoft.java.debug.core.adapter;

public interface IVirtualMachineManager extends com.sun.jdi.VirtualMachineManager {
    boolean connectVirtualMachine(com.sun.jdi.VirtualMachine vm);

    boolean disconnectVirtualMachine(com.sun.jdi.VirtualMachine vm);
}
