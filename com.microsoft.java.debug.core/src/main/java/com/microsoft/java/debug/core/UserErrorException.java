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

public class UserErrorException extends DebugException {
    private static final long serialVersionUID = -6001456457602995764L;

    /**
     * Create an user error exception indicates a user setting/operation is illegal by design.
     */
    public UserErrorException() {
        super();
    }


    /**
     * Create an user error exception indicates a user setting/operation is illegal by design.
     *
     * @param message the error message
     */
    public UserErrorException(String message) {
        super(message);
    }
}
