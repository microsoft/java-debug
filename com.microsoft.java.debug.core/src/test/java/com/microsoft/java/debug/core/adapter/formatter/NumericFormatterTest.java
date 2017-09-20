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

import com.sun.jdi.DoubleValue;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

import static com.microsoft.java.debug.core.adapter.formatter.NumericFormatter.NUMERIC_FORMAT_OPTION;
import static com.microsoft.java.debug.core.adapter.formatter.NumericFormatter.NUMERIC_PRECISION_OPTION;
import static org.junit.Assert.*;

public class NumericFormatterTest extends BaseFormatterTest {
    protected NumericFormatter formatter;
    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new NumericFormatter();
    }

    @Test
    public void testAcceptType() throws Exception {
        LocalVariable i = this.getLocalVariable("i");

        assertFalse("NumericFormatter should accept null.", formatter.acceptType(null, new HashMap<>()));
        assertTrue("NumericFormatter should accept int type.", formatter.acceptType(i.type(), new HashMap<>()));
        ObjectReference integer = this.getObjectReference("java.lang.Integer");
        assertFalse("NumericFormatter should not accept Integer type.", formatter.acceptType(integer.type(), new HashMap<>()));
        assertFalse("NumericFormatter should not accept Object type.", formatter.acceptType(this.getLocalVariable("obj").type(), new HashMap<>()));
        assertFalse("NumericFormatter should not accept array type.", formatter.acceptType(this.getLocalVariable("arrays").type(), new HashMap<>()));
        assertFalse("NumericFormatter should not accept String type.", formatter.acceptType(this.getLocalVariable("str").type(), new HashMap<>()));

        VirtualMachine vm = getVM();
        assertFalse("NumericFormatter should not accept boolean type.",
            formatter.acceptType(vm.mirrorOf(true).type(), new HashMap<>()));

        assertFalse("NumericFormatter should not accept char type.",
            formatter.acceptType(vm.mirrorOf('c').type(), new HashMap<>()));

        assertTrue("NumericFormatter should accept long type.",
            formatter.acceptType(vm.mirrorOf(1L).type(), new HashMap<>()));

        assertTrue("NumericFormatter should accept float type.",
            formatter.acceptType(vm.mirrorOf(1.2f).type(), new HashMap<>()));

        assertTrue("NumericFormatter should accept double type.",
            formatter.acceptType(vm.mirrorOf(1.2).type(), new HashMap<>()));

        assertTrue("NumericFormatter should accept byte type.",
            formatter.acceptType(vm.mirrorOf((byte)12).type(), new HashMap<>()));
    }

    @Test
    public void testToString() throws Exception {
        Value i = this.getLocalValue("i");
        assertEquals("NumericFormatter should be able to format int correctly.", "111", formatter.toString(i, new HashMap<>()));


        VirtualMachine vm = getVM();

        assertEquals("NumericFormatter should be able to format double correctly.", "111.000000",
            formatter.toString(vm.mirrorOf(111.0), new HashMap<>()));
        assertEquals("NumericFormatter should be able to format float correctly.", "111.000000",
            formatter.toString(vm.mirrorOf(111.0f), new HashMap<>()));

        Map<String, Object> options = formatter.getDefaultOptions();
        options.put(NUMERIC_PRECISION_OPTION, 1);
        assertEquals("NumericFormatter should be able to format double correctly.", "111.0",
            formatter.toString(vm.mirrorOf(111.0), options));
        assertEquals("NumericFormatter should be able to format float correctly.", "111.0",
            formatter.toString(vm.mirrorOf(111.0f), options));

        options.put(NUMERIC_PRECISION_OPTION, -1);
        assertEquals("NumericFormatter should be able to format double correctly.", "111.000000",
            formatter.toString(vm.mirrorOf(111.0), options));
    }

    @Test
    public void testToHexOctString() throws Exception {
        Value i = this.getLocalValue("i");

        Map<String, Object> options = formatter.getDefaultOptions();
        options.put(NUMERIC_FORMAT_OPTION, NumericFormatEnum.HEX);
        assertEquals("NumericFormatter should be able to format an hex integer.",
            "0x" + Integer.toHexString(111), formatter.toString(i, options));


        options.put(NUMERIC_FORMAT_OPTION, NumericFormatEnum.OCT);
        assertEquals("NumericFormatter should be able to format an oct integer.",
            "0" +Integer.toOctalString(111), formatter.toString(i, options));
    }

    @Test
    public void testValueOf() throws Exception {

        Value i = this.getLocalValue("i");
        Map<String, Object> options = formatter.getDefaultOptions();
        Value newValue = formatter.valueOf(formatter.toString(i, options), i.type(), options);
        assertNotNull("NumericFormatter should be able to create integer by string.", newValue);
        assertTrue("Should create an integer value.", newValue instanceof IntegerValue);
        assertEquals("Should create an integer with right value.", "111", newValue.toString());

        options.put(NUMERIC_FORMAT_OPTION, NumericFormatEnum.HEX);

        newValue = formatter.valueOf(formatter.toString(i, options), i.type(), options);
        assertNotNull("NumericFormatter should be able to create integer by string.", newValue);
        assertTrue("Should create an integer value.", newValue instanceof IntegerValue);
        assertEquals("Should create an integer with right value.", "111", newValue.toString());

        options.put(NUMERIC_FORMAT_OPTION, NumericFormatEnum.OCT);
        newValue = formatter.valueOf(formatter.toString(i, options), i.type(), options);
        assertNotNull("NumericFormatter should be able to create integer by string.", newValue);
        assertTrue("Should create an integer value.", newValue instanceof IntegerValue);
        assertEquals("Should create an integer with right value.", "111", newValue.toString());


        newValue = formatter.valueOf("-12121212", i.type(), options);
        assertNotNull("NumericFormatter should be able to create integer by string.", newValue);
        assertTrue("Should create an integer value.", newValue instanceof IntegerValue);
        assertEquals("Should create an integer with right value.", "-12121212", newValue.toString());

        newValue = formatter.valueOf("0", i.type(), options);
        assertNotNull("NumericFormatter should be able to create integer by string.", newValue);
        assertTrue("Should create an integer value.", newValue instanceof IntegerValue);
        assertEquals("Should create an integer with right value.", "0", newValue.toString());

        VirtualMachine vm = getVM();

        newValue = formatter.valueOf("0", vm.mirrorOf(10.0f).type(), options);
        assertNotNull("NumericFormatter should be able to create float by string.", newValue);
        assertTrue("Should create an float value.", newValue instanceof FloatValue);
        assertEquals("Should create an float with right value.", "0.0", newValue.toString());


        newValue = formatter.valueOf("10.0", vm.mirrorOf(10.0).type(), options);
        assertNotNull("NumericFormatter should be able to create double by string.", newValue);
        assertTrue("Should create an double value.", newValue instanceof DoubleValue);
        assertEquals("Should create an double with right value.", "10.0", newValue.toString());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValueOfNotSupported() {
        ObjectReference or = this.getObjectReference("Foo");
        formatter.valueOf("new Foo()", or.referenceType(), new HashMap<>());
        fail("Set value for object is not supported.");
    }


    @Test(expected = NumberFormatException.class)
    public void testValueOfNotIllegalInput() {
        formatter.valueOf("new Foo()", getVM().mirrorOf(10.0).type(), new HashMap<>());
        fail("Set value for object is not supported.");
    }


    @Test(expected = UnsupportedOperationException.class)
    public void testToStringNotSupported() {
        ObjectReference or = this.getObjectReference("Foo");
        formatter.toString(or, new HashMap<>());
        fail("format object should not be supported by numeric formatter.");
    }

    @Test
    public void testDefaultOptions() {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertNotNull("Default options should never be null.", options);
        assertEquals("Default options for numeric formatter should have two options.", 2, options.size());
    }
}
