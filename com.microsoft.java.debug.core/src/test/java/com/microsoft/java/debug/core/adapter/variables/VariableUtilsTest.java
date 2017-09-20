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

package com.microsoft.java.debug.core.adapter.variables;

import org.junit.Before;
import org.junit.Test;

import com.microsoft.java.debug.core.adapter.BaseJdiTestCase;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import static org.junit.Assert.*;

public class VariableUtilsTest extends BaseJdiTestCase {

    @Before
    public void setup() throws Exception {
        super.setup();
    }

    @Test
    public void testHasChildren() throws Exception {
        ObjectReference foo = this.getObjectReference("Foo");
        assertTrue("class Foo should have children", VariableUtils.hasChildren(foo, true));
        assertTrue("class Foo should have children", VariableUtils.hasChildren(foo, false));

        Value string = this.getLocalValue("str");

        assertTrue("String object should have children", VariableUtils.hasChildren(string, true));
        assertTrue("String object should have children", VariableUtils.hasChildren(string, false));

        ArrayReference arrays = (ArrayReference) this.getLocalValue("arrays");
        assertTrue("Array object with elements should have children", VariableUtils.hasChildren(
            arrays, true));
        assertTrue("Array object with elements should have children", VariableUtils.hasChildren(
            arrays, false));

        assertFalse("Array object with no elements should not have children", VariableUtils.hasChildren(
            ((ArrayType) arrays.type()).newInstance(0), true));

        assertFalse("Array object with no elements should not have children", VariableUtils.hasChildren(
            ((ArrayType) arrays.type()).newInstance(0), false));

        assertTrue("List object with elements should have children", VariableUtils.hasChildren(
            this.getLocalValue("strList"), false));

        assertFalse("Object should not have children", VariableUtils.hasChildren(this.getLocalValue("obj"), true));
        assertFalse("Object should not have children", VariableUtils.hasChildren(this.getLocalValue("obj"), false));

        assertTrue("Class object should have children", VariableUtils.hasChildren(this.getLocalValue("b"), false));
        assertTrue("Class object should have children", VariableUtils.hasChildren(this.getLocalValue("b"), false));

        assertFalse("Null object should not have children", VariableUtils.hasChildren(null, true));
        assertFalse("Null object should not have children", VariableUtils.hasChildren(null, false));

        assertTrue("Boolean object should have children", VariableUtils.hasChildren(getLocalValue("boolVar"), false));
        assertFalse("boolean object should not have children", VariableUtils.hasChildren(getVM().mirrorOf(true), false));

        assertFalse("Class with no fields should not have children", VariableUtils.hasChildren(this.getLocalValue("a"), true));
        assertFalse("Class with no fields should not have children", VariableUtils.hasChildren(this.getLocalValue("a"), false));

        assertFalse("Primitive object should not have children", VariableUtils.hasChildren(
            getVM().mirrorOf(1), true));
        assertFalse("Primitive object should not have children", VariableUtils.hasChildren(
            getVM().mirrorOf(true), true));
        assertFalse("Primitive object should not have children", VariableUtils.hasChildren(
            getVM().mirrorOf('c'), true));
        assertFalse("Primitive object should not have children", VariableUtils.hasChildren(
            getVM().mirrorOf(1000L), true));

    }

    @Test
    public void testGetThisVariable() throws Exception {
        assertNull("Should return null on static method.", VariableUtils.getThisVariable(getStackFrame()));
    }

}
