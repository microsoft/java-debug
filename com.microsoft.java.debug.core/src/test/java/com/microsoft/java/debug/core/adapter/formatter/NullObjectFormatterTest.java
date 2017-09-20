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

import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NullObjectFormatterTest extends BaseFormatterTest{
    protected NullObjectFormatter formatter;
    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new NullObjectFormatter();
    }

    @Test
    public void testAcceptType() throws Exception {
        ObjectReference foo = this.getObjectReference("Foo");

        assertTrue("Should accept null type.", formatter.acceptType(null, new HashMap<>()));

        assertFalse("Should not accept Foo type.", formatter.acceptType(foo.referenceType(), new HashMap<>()));

        ObjectReference str = this.getObjectReference("java.lang.String");
        assertFalse("Should not accept String type.", formatter.acceptType(str.referenceType(), new HashMap<>()));

        ObjectReference clz = this.getObjectReference("java.lang.Class");
        assertFalse("Should not accept Class type.", formatter.acceptType(clz.referenceType(), new HashMap<>()));

        LocalVariable list = this.getLocalVariable("strList");
        assertFalse("Should not accept List type.", formatter.acceptType(list.type(), new HashMap<>()));

        LocalVariable arrays = this.getLocalVariable("arrays");
        assertFalse("Should not accept array type.", formatter.acceptType(arrays.type(), new HashMap<>()));
    }

    @Test
    public void testToString() throws Exception {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertEquals("Should be able to format string type.", "null",
            formatter.toString(null, options));

        assertEquals("Should be able to format string type.", "null",
            formatter.toString(null, options));
    }

    @Test
    public void testValueOf() throws Exception {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertNull("Should return null for null formatter", formatter.valueOf("null", null, options));
        assertNull("Should return null for null formatter", formatter.valueOf(null, null, options));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValueOfNotSupported() {
        ObjectReference or = this.getObjectReference("Foo");
        formatter.valueOf("new Foo()", or.referenceType(), new HashMap<>());
    }
}
