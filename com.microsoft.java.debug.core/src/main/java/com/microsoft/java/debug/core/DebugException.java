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

public class DebugException extends Exception {
    private static final long serialVersionUID = 1L;
    private int errorCode;

    private boolean userError = false;

    public DebugException() {
        super();
    }

    public DebugException(String message) {
        super(message);
    }

    public DebugException(String message, Throwable cause) {
        super(message, cause);
    }

    public DebugException(Throwable cause) {
        super(cause);
    }

    public DebugException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a debug exception with userError flag.
     * @param message the error message
     * @param errorCode the error code
     * @param userError the boolean value indicating whether this exception is caused by a known user error
     */
    public DebugException(String message, int errorCode, boolean userError) {
        super(message);
        this.errorCode = errorCode;
        this.userError = userError;
    }

    public DebugException(String message, Throwable cause, int errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public DebugException(Throwable cause, int errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return this.errorCode;
    }

    public void setUserError(boolean userError) {
        this.userError = userError;
    }

    public boolean isUserError() {
        return this.userError;
    }
}
