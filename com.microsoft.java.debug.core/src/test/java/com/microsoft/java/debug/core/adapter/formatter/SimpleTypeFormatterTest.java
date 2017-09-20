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

import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import static com.microsoft.java.debug.core.adapter.formatter.NumericFormatter.NUMERIC_FORMAT_OPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimpleTypeFormatterTest extends BaseFormatterTest {
    protected SimpleTypeFormatter formatter;
    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new SimpleTypeFormatter();
    }

    @Test
    public void testAcceptType() throws Exception {
        Value boolVar = getVM().mirrorOf(true);
        assertTrue("Should accept any type.", formatter.acceptType(boolVar.type(), new HashMap<>()));

        ObjectReference or = this.getObjectReference("Foo");

        assertTrue("Should accept any type.", formatter.acceptType(null, new HashMap<>()));
        assertTrue("Should accept any type.", formatter.acceptType(or.type(), new HashMap<>()));

        ObjectReference str = this.getObjectReference("java.lang.String");
        assertTrue("Should accept String type.", formatter.acceptType(str.referenceType(), new HashMap<>()));
    }

    @Test
    public void testToString() throws Exception {
        Value boolVar = getVM().mirrorOf(true);
        Map<String, Object> options = formatter.getDefaultOptions();
        assertEquals("Failed to format boolean type.", boolVar.type().name(),
            formatter.toString(boolVar.type(), options));
        ObjectReference foo = this.getObjectReference("Foo");
        assertEquals("Failed to format Foo type.", foo.type().name(),
            formatter.toString(foo.type(), options));
        ObjectReference str = this.getObjectReference("java.lang.String");

        assertEquals("Failed to format String type.", "String",
            formatter.toString(str.type(), options));

        options.put(SimpleTypeFormatter.QUALIFIED_CLASS_NAME_OPTION, true);
        assertEquals("Failed to format boolean type.", boolVar.type().name(),
            formatter.toString(boolVar.type(), options));
        assertEquals("Failed to format Foo type.", foo.type().name(),
            formatter.toString(foo.type(), options));
        assertEquals("Failed to format String type.", "java.lang.String",
            formatter.toString(str.type(), options));
    }
}
