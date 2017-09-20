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

package com.microsoft.java.debug.core.adapter.formatter;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.microsoft.java.debug.core.adapter.BaseJdiTestCase;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class ArrayObjectFormatterTest extends BaseJdiTestCase {
    protected ArrayObjectFormatter formatter;
    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new ArrayObjectFormatter((type, opts) -> type.name());
    }

    @Test
    public void testAcceptType() throws Exception {
        ObjectReference foo = this.getObjectReference("Foo");

        assertFalse("Should not accept null type.", formatter.acceptType(null, new HashMap<>()));

        assertFalse("Should not accept Foo type.", formatter.acceptType(foo.referenceType(), new HashMap<>()));

        ObjectReference str = this.getObjectReference("java.lang.String");
        assertFalse("Should not accept String type.", formatter.acceptType(str.referenceType(), new HashMap<>()));

        ObjectReference clz = this.getObjectReference("java.lang.Class");
        assertFalse("Should not accept Class type.", formatter.acceptType(clz.referenceType(), new HashMap<>()));

        LocalVariable list = this.getLocalVariable("strList");
        assertFalse("Should not accept List type.", formatter.acceptType(list.type(), new HashMap<>()));

        LocalVariable arrays = this.getLocalVariable("arrays");
        assertTrue("Should accept array type.", formatter.acceptType(arrays.type(), new HashMap<>()));
    }

    @Test
    public void testToString() throws Exception {
        Value arrays = this.getLocalValue("arrays");
        assertEquals("Should be able to format array type.", String.format("int[1] (id=%d)",
            ((ObjectReference) arrays).uniqueID()),
            formatter.toString(arrays, new HashMap<>()));
    }

    @Test
    public void testDefaultOptions() {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertNotNull("Default options should never be null.", options);
        assertEquals("ArrayObjectFormatter should have no options.", 0, options.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValueOfNotUnsupported() {
        try {
            ObjectReference array = (ObjectReference)this.getLocalValue("arrays");
            formatter.valueOf("new int[] { 1, 2, 3}", array.referenceType(), new HashMap<>());
        } catch (AbsentInformationException e) {
            e.printStackTrace();
            fail("Failure due to exception.");
        }

    }
}
