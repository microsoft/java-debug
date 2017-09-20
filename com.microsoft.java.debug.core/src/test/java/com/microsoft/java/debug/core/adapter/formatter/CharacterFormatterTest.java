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
import com.sun.jdi.CharValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CharacterFormatterTest extends BaseFormatterTest {
    private CharacterFormatter formatter;
    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new CharacterFormatter();
    }

    @Test
    public void testAcceptType() throws Exception {
        ObjectReference foo = this.getObjectReference("Foo");

        assertFalse("Should not accept null type.", formatter.acceptType(null, new HashMap<>()));

        assertFalse("Should not accept Foo type.", formatter.acceptType(foo.referenceType(), new HashMap<>()));

        ObjectReference str = this.getObjectReference("java.lang.String");
        assertFalse("Should not accept String type.", formatter.acceptType(str.referenceType(), new HashMap<>()));

        Value boolVar = getVM().mirrorOf(true);
        assertFalse("Should not accept boolean type.", formatter.acceptType(boolVar.type(), new HashMap<>()));

        boolVar = this.getLocalValue("boolVar");
        assertFalse("Should not accept Boolean type.", formatter.acceptType(boolVar.type(), new HashMap<>()));

        Value charVar = getVM().mirrorOf('c');
        assertTrue("Should accept char type.", formatter.acceptType(charVar.type(), new HashMap<>()));
    }

    @Test
    public void testToString() throws Exception {
        Map<String, Object> options = formatter.getDefaultOptions();
        Value charVar = getVM().mirrorOf('c');
        assertEquals("Should be able to format char type.", "c",
            formatter.toString(charVar, options));

        charVar = getVM().mirrorOf('C');
        assertEquals("Should be able to format char type.", "C",
            formatter.toString(charVar, options));

        charVar = getVM().mirrorOf('?');
        assertEquals("Should be able to format char type.", "?",
            formatter.toString(charVar, options));

        charVar = getVM().mirrorOf('中');
        assertEquals("Should be able to format char type.", "中",
            formatter.toString(charVar, options));
    }

    @Test
    public void testValueOf() throws Exception {
        Map<String, Object> options = formatter.getDefaultOptions();
        Value charVar = getVM().mirrorOf('c');
        Value newValue = formatter.valueOf("M", charVar.type(), options);
        assertTrue("should return char type", newValue instanceof CharValue);
        assertEquals("should return char 'M'", 'M', ((CharValue)newValue).value());

        newValue = formatter.valueOf("'N'", charVar.type(), options);
        assertTrue("should return char type", newValue instanceof CharValue);
        assertEquals("should return char 'N'", 'N', ((CharValue)newValue).value());

        newValue = formatter.valueOf("?", charVar.type(), options);
        assertTrue("should return char type", newValue instanceof CharValue);
        assertEquals("should return char '?", '?', ((CharValue)newValue).value());

        newValue = formatter.valueOf("/", charVar.type(), options);
        assertTrue("should return char type", newValue instanceof CharValue);
        assertEquals("should return char '/'", '/', ((CharValue)newValue).value());

        newValue = formatter.valueOf("'", charVar.type(), options);
        assertTrue("should return char type", newValue instanceof CharValue);
        assertEquals("should return char '", '\'', ((CharValue)newValue).value());
    }

    @Test
    public void testDefaultOptions() {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertNotNull("Default options should never be null.", options);
        assertEquals("Default options for char formatter should not have options.", 0, options.size());
    }
}
