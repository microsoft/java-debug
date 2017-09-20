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
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;

import static com.microsoft.java.debug.core.adapter.formatter.NumericFormatter.NUMERIC_FORMAT_OPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ObjectFormatterTest extends BaseJdiTestCase {
    protected ObjectFormatter formatter;

    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new ObjectFormatter((type, opts) -> "MockType");
    }


    @Test
    public void testAcceptType() throws Exception {
        ObjectReference or = this.getObjectReference("Foo");

        assertFalse("Should not accept null type.", formatter.acceptType(null, new HashMap<>()));

        assertTrue("Should accept Foo type.", formatter.acceptType(or.referenceType(), new HashMap<>()));

        ObjectReference str = this.getObjectReference("java.lang.String");
        assertTrue("Should accept String type.", formatter.acceptType(str.referenceType(), new HashMap<>()));

        ObjectReference clz = this.getObjectReference("java.lang.Class");
        assertTrue("Should accept Class type.", formatter.acceptType(clz.referenceType(), new HashMap<>()));

        LocalVariable list = this.getLocalVariable("strList");
        assertTrue("Should accept List type.", formatter.acceptType(list.type(), new HashMap<>()));

        LocalVariable arrays = this.getLocalVariable("arrays");
        assertTrue("Should accept array type.", formatter.acceptType(arrays.type(), new HashMap<>()));
    }

    @Test
    public void testToStringDec() throws Exception {
        ObjectReference or = this.getObjectReference("Foo");
        assertEquals("Failed to format an object.", String.format("MockType (id=%d)", or.uniqueID()),
            formatter.toString(or, new HashMap<>()));
    }

    @Test
    public void testToStringHex() throws Exception {
        ObjectReference or = this.getObjectReference("Foo");
        Map<String, Object> options = formatter.getDefaultOptions();
        options.put(NUMERIC_FORMAT_OPTION, NumericFormatEnum.HEX);
        assertEquals("Failed to format an object.", String.format("MockType (id=%#x)", or.uniqueID()),
            formatter.toString(or, options));
    }

    @Test
    public void testToStringOct() throws Exception {
        ObjectReference or = this.getObjectReference("Foo");
        Map<String, Object> options = formatter.getDefaultOptions();
        options.put(NUMERIC_FORMAT_OPTION, NumericFormatEnum.OCT);
        assertEquals("Failed to format an object.", String.format("MockType (id=%#o)", or.uniqueID()),
            formatter.toString(or, options));
    }

    @Test
    public void testValueOfNull() throws Exception {
        ObjectReference or = this.getObjectReference("Foo");
        assertNull("Should return null for evaluating \"null\".", formatter.valueOf("null", or.referenceType(), new HashMap<>()));
        assertNull("Should return null for evaluating null.", formatter.valueOf(null, or.referenceType(), new HashMap<>()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValueOfNotUnsupported() {
        ObjectReference or = this.getObjectReference("Foo");
        formatter.valueOf("new Foo()", or.referenceType(), new HashMap<>());
        fail("Set value for object is not supported.");
    }

    @Test
    public void testDefaultOptions() {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertNotNull("Default options should never be null.", options);
        assertEquals("ObjectFormatter should have no options.", 0, options.size());
    }
}
