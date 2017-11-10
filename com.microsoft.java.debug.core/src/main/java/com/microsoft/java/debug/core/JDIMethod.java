/*******************************************************************************
 * Copyright (c) 2010, 2014 Jesper Steen Moller and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jesper Steen Moller - initial API and implementation
 *     IBM Corporation - Bug fixing
 *******************************************************************************/

package com.microsoft.java.debug.core;

import com.sun.jdi.Method;

/**
 * Class for analysing Java methods while debugging.
 *
 * @author jmoeller2
 *
 */
public class JDIMethod {

    // Known Java byte codes, from the JVM spec
    private static final int ALOAD_0 = 0x2a;

    private static final int ILOAD_1 = 0x1b;
    private static final int LLOAD_1 = 0x1f;
    private static final int FLOAD_1 = 0x23;
    private static final int DLOAD_1 = 0x27;
    private static final int ALOAD_1 = 0x2b;

    private static final int IRETURN = 0xac;
    private static final int LRETURN = 0xad;
    private static final int FRETURN = 0xae;
    private static final int DRETURN = 0xaf;
    private static final int ARETURN = 0xb0;

    private static final int GETFIELD = 0xb4;
    private static final int PUTFIELD = 0xb5;

    private static final int RETURN = 0xb1;

    /**
     * Determines if the opcode passes in is one of the value return
     * instructions.
     *
     * @param opCode
     *            opCode to check
     * @return If <code>opCode</code> is one of 'areturn', 'ireturn', etc.
     */
    public static final boolean isXReturn(byte opCode) {
        return (opCode & 0xFF) == IRETURN || (opCode & 0xFF) == LRETURN
                || (opCode & 0xFF) == FRETURN || (opCode & 0xFF) == DRETURN
                || (opCode & 0xFF) == ARETURN;
    }

    /**
     * Determines if the opcode passes in is one of the 'loado_1' instruxtions.
     *
     * @param opCode
     *            opCode to check
     * @return If <code>opCode</code> is one of 'aload_1', 'iload_1', etc.
     */
    public static final boolean isXLoad1(byte opCode) {
        return (opCode & 0xFF) == ILOAD_1 || (opCode & 0xFF) == LLOAD_1
                || (opCode & 0xFF) == FLOAD_1 || (opCode & 0xFF) == DLOAD_1
                || (opCode & 0xFF) == ALOAD_1;
    }

    /**
     * Determines if the method in question is a simple getter, JavaBean style.
     * Simple getters have byte code which look like this, and they start with
     * "get" or "is":
     * 0 aload_0 1 getfield 4 Xreturn
     *
     * @param method
     *            Method to check
     * @return true if the method is a simple getter
     */
    public static boolean isGetterMethod(Method method) {
        if (!(method.name().startsWith("get") || method.name().startsWith("is"))) {
            return false;
        }

        byte[] bytecodes = method.bytecodes();
        return bytecodes.length == 5 && (bytecodes[0] & 0xFF) == ALOAD_0
                && (bytecodes[1] & 0xFF) == GETFIELD && isXReturn(bytecodes[4]);
    }

    /**
     * Determines if the method in question is a simple getter, JavaBean style.
     * Simple setters have byte code which look like this, and they start with
     * "set":
     * 0 aload_0 1 Xload_1 2 putfield 5 return
     *
     * @param method
     *            Method to check
     * @return true if the method is a simple setter
     */
    public static boolean isSetterMethod(Method method) {
        if (!method.name().startsWith("set")) { //$NON-NLS-1$
            return false;
        }

        byte[] bytecodes = method.bytecodes();
        return bytecodes.length == 6 && (bytecodes[0] & 0xFF) == ALOAD_0
                && isXLoad1(bytecodes[1]) && (bytecodes[2] & 0xFF) == PUTFIELD
                && (bytecodes[5] & 0xFF) == RETURN;
    }
}

