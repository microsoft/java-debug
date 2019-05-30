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

import static com.microsoft.java.debug.core.adapter.formatter.StringObjectFormatter.MAX_STRING_LENGTH_OPTION;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.microsoft.java.debug.core.adapter.BaseJdiTestCase;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;

public class StringObjectFormatterTest extends BaseJdiTestCase {
    protected StringObjectFormatter formatter;
    @Override
    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new StringObjectFormatter();
    }

    @Test
    public void testAcceptType() throws Exception {
        ObjectReference foo = this.getObjectReference("Foo");

        assertFalse("Should not accept null type.", formatter.acceptType(null, new HashMap<>()));

        assertFalse("Should not accept Foo type.", formatter.acceptType(foo.referenceType(), new HashMap<>()));

        ObjectReference str = this.getObjectReference("java.lang.String");
        assertTrue("Should accept String type.", formatter.acceptType(str.referenceType(), new HashMap<>()));

        ObjectReference clz = this.getObjectReference("java.lang.Class");
        assertFalse("Should not accept Class type.", formatter.acceptType(clz.referenceType(), new HashMap<>()));

        LocalVariable list = this.getLocalVariable("strList");
        assertFalse("Should not accept List type.", formatter.acceptType(list.type(), new HashMap<>()));

        LocalVariable arrays = this.getLocalVariable("arrays");
        assertFalse("Should not accept array type.", formatter.acceptType(arrays.type(), new HashMap<>()));
    }

    @Test
    public void testToString() throws Exception {
        Value string = this.getLocalValue("str");
        Map<String, Object> options = formatter.getDefaultOptions();
        options.put(MAX_STRING_LENGTH_OPTION, 4);
        assertEquals("Should be able to format string type.", "\"s...\"",
            formatter.toString(string, options));

        options.put(MAX_STRING_LENGTH_OPTION, 5);
        assertEquals("Should be able to format string type.", "\"st...\"",
            formatter.toString(string, options));
        assertTrue("Should not trim long string by default",
            formatter.toString(string, new HashMap<>()).contains(((StringReference) string).value()));
    }

    @Test
    public void testValueOf() throws Exception {
        Map<String, Object> options = formatter.getDefaultOptions();
        Value string = this.getLocalValue("str");
        StringReference newValue = (StringReference) formatter.valueOf("aaa", string.type(), options);

        assertNotNull("StringObjectFormatter should be able to create string.", newValue);
        assertEquals("Should create a String with right value.", "aaa", newValue.value());


        newValue = (StringReference) formatter.valueOf("\"aaa\"", string.type(), options);

        assertNotNull("StringObjectFormatter should be able to create string.", newValue);
        assertEquals("Should create a String with right value.", "aaa", newValue.value());


        newValue = (StringReference) formatter.valueOf("\"aaa", string.type(), options);

        assertNotNull("StringObjectFormatter should be able to create string.", newValue);
        assertEquals("Should create a String with right value.", "\"aaa", newValue.value());

        assertNull("StringObjectFormatter should be able to create null string.",
            formatter.valueOf("null", string.type(), options));
        assertNull("StringObjectFormatter should be able to create null string.",
            formatter.valueOf(null, string.type(), options));
    }


    @Test
    public void testDefaultOptions() {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertNotNull("Default options should never be null.", options);
        assertEquals("Default options for numeric formatter should have two options.", 1, options.size());
        assertTrue("Should contains max string length.", options.containsKey(StringObjectFormatter.MAX_STRING_LENGTH_OPTION));
    }
}
