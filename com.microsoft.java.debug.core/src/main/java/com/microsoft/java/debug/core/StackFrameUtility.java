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

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.NativeMethodException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;

public final class StackFrameUtility {

    public static boolean isNative(StackFrame frame) {
        return frame.location().method().isNative();
    }

    /**
     * Pop a StackFrame from its thread.
     *
     * @param frame
     *            the StackFrame will be popped
     */
    public static void pop(StackFrame frame) throws DebugException {
        try {
            frame.thread().popFrames(frame);
        } catch (IncompatibleThreadStateException e) {
            throw new DebugException(String.format("%s occurred popping stack frame.", e.getMessage()), e);
        } catch (InvalidStackFrameException e) {
            throw new DebugException("Cannot pop up the top stack farme.", e);
        } catch (NativeMethodException e) {
            throw new DebugException("Cannot pop up the stack frame because it is not valid for a native method.", e);
        } catch (RuntimeException e) {
            throw new DebugException(String.format("Runtime exception happened: %s", e.getMessage()), e);
        }
    }

    public static String getName(StackFrame frame) {
        return frame.location().method().name();
    }

    public static String getSignature(StackFrame frame) {
        return frame.location().method().signature();
    }

    public static boolean isObsolete(StackFrame frame) {
        return frame.location().method().isObsolete();
    }

    /**
     * Get the StackFrame associated source file path.
     *
     * @param frame
     *            StackFrame for the source path
     * @return the source file path
     */
    public static String getSourcePath(StackFrame frame) {
        try {
            return frame.location().sourcePath();
        } catch (AbsentInformationException e) {
            // Ignore it
        }
        return null;
    }

    public static ReferenceType getDeclaringType(StackFrame frame) {
        return frame.location().method().declaringType();
    }
}
