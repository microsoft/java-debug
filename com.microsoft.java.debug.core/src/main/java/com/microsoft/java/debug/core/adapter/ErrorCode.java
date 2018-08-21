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

package com.microsoft.java.debug.core.adapter;

import java.util.Arrays;

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
    EMPTY_DEBUG_SESSION(1011),
    INVALID_ENCODING(1012),
    VM_TERMINATED(1013),
    LAUNCH_IN_TERMINAL_FAILURE(1014),
    STEP_FAILURE(1015),
    RESTARTFRAME_FAILURE(1016),
    COMPLETIONS_FAILURE(1017),
    EVALUATION_COMPILE_ERROR(2001),
    EVALUATE_NOT_SUSPEND(2002);

    private int id;

    ErrorCode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /**
     * Get the corresponding ErrorCode type by the error code id.
     * If the error code is not defined in the enum type, return ErrorCode.UNKNOWN_FAILURE.
     * @param id
     *             the error code id.
     * @return the ErrorCode type.
     */
    public static ErrorCode parse(int id) {
        ErrorCode[] found = Arrays.stream(ErrorCode.values()).filter(code -> {
            return code.getId() == id;
        }).toArray(ErrorCode[]::new);

        if (found.length > 0) {
            return found[0];
        }
        return ErrorCode.UNKNOWN_FAILURE;
    }
}
