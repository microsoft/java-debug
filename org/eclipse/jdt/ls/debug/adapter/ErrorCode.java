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

package org.eclipse.jdt.ls.debug.adapter;

public enum ErrorCode {
    UNKNOWN_FAILURE(1000),
    UNRECOGNIZED_REQUEST_FAILURE(1001),
    LAUNCH_FAILURE(1002),
    ATTACH_FAILURE(1003),
    ARGUMENT_MISSING(1004),
    SET_BREAKPOINT_FAILURE(1005),
    SET_EXCEPTIONBREAKPOINT_FAILURE(1006),
    GET_STACKTRACE_FAILURE(1007),
    GET_VARIABLE_FAILURE(1008),
    SET_VARIABLE_FAILURE(1009),
    EVALUATE_FAILURE(1010),
    EMPTY_DEBUG_SESSION(1011);

    private int id;

    ErrorCode(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}
