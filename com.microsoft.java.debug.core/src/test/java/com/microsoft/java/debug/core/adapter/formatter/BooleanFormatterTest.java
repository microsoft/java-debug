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

import com.sun.jdi.BooleanValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BooleanFormatterTest extends BaseFormatterTest {
    protected BooleanFormatter formatter;
    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new BooleanFormatter();
    }

    @Test
    public void testAcceptType() throws Exception {
        ObjectReference foo = this.getObjectReference("Foo");

        assertFalse("Should not accept null type.", formatter.acceptType(null, new HashMap<>()));

        assertFalse("Should not accept Foo type.", formatter.acceptType(foo.referenceType(), new HashMap<>()));

        ObjectReference str = this.getObjectReference("java.lang.String");
        assertFalse("Should not accept String type.", formatter.acceptType(str.referenceType(), new HashMap<>()));

        Value boolVar = getVM().mirrorOf(true);
        assertTrue("Should accept boolean type.", formatter.acceptType(boolVar.type(), new HashMap<>()));

        boolVar = this.getLocalValue("boolVar");
        assertFalse("Should not accept Boolean type.", formatter.acceptType(boolVar.type(), new HashMap<>()));
    }

    @Test
    public void testToString() throws Exception {
        Map<String, Object> options = formatter.getDefaultOptions();
        Value boolVar = getVM().mirrorOf(true);
        assertEquals("Should be able to format boolean type.", "true",
            formatter.toString(boolVar, options));
        boolVar = getVM().mirrorOf(false);
        assertEquals("Should be able to format boolean type.", "false",
            formatter.toString(boolVar, options));
    }

    @Test
    public void testValueOf() throws Exception {
        Map<String, Object> options = formatter.getDefaultOptions();
        Value boolVar = getVM().mirrorOf(true);
        Value newValue = formatter.valueOf("true", boolVar.type(), options);
        assertTrue("should return boolean type", newValue instanceof BooleanValue);
        assertTrue("should return boolean type", ((BooleanValue)newValue).value());
        assertEquals("Should be able to restore boolean value.", "true",
            formatter.toString(newValue, options));

        newValue = formatter.valueOf("True", boolVar.type(), options);
        assertTrue("should return boolean type", newValue instanceof BooleanValue);
        assertTrue("should return boolean type", ((BooleanValue)newValue).value());

        newValue = formatter.valueOf("false", boolVar.type(), options);
        assertTrue("should return boolean type", newValue instanceof BooleanValue);
        assertFalse("should return boolean 'false'", ((BooleanValue)newValue).value());

        newValue = formatter.valueOf("False", boolVar.type(), options);
        assertTrue("should return boolean type", newValue instanceof BooleanValue);
        assertFalse("should return boolean 'false'", ((BooleanValue)newValue).value());

        newValue = formatter.valueOf("abc", boolVar.type(), options);
        assertTrue("should return boolean type", newValue instanceof BooleanValue);
        assertFalse("should return boolean 'false'", ((BooleanValue)newValue).value());
    }

    @Test
    public void testDefaultOptions() {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertNotNull("Default options should never be null.", options);
        assertEquals("Default options for boolean formatter should not have options.", 0, options.size());
    }
}
