/*******************************************************************************
* Copyright (c) 2018-2021 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core;

import com.sun.jdi.connect.VMStartException;

/**
 * Extends {@link VMStartException} to provide more detail about the failed process
 * from before it is destroyed.
 */
public class LaunchException extends VMStartException {

    boolean exited;
    int exitStatus;
    String stdout;
    String stderr;

    public LaunchException(String message, Process process, boolean exited, int exitStatus, String stdout, String stderr) {
        super(message, process);
        this.exited = exited;
        this.exitStatus = exitStatus;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public boolean isExited() {
        return exited;
    }

    public int getExitStatus() {
        return exitStatus;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

}
