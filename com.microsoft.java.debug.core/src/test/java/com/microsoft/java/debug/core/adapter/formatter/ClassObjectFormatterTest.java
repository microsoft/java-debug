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
import com.sun.jdi.Value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClassObjectFormatterTest extends BaseJdiTestCase {
    private ClassObjectFormatter formatter;
    @Before
    public void setup() throws Exception {
        super.setup();
        formatter = new ClassObjectFormatter((type, opts) -> type.name());
    }

    @Test
    public void testAcceptType() throws Exception {
        ObjectReference foo = this.getObjectReference("Foo");

        assertFalse("Should not accept null type.", formatter.acceptType(null, new HashMap<>()));

        assertFalse("Should not accept Foo type.", formatter.acceptType(foo.referenceType(), new HashMap<>()));

        ObjectReference str = this.getObjectReference("java.lang.String");
        assertFalse("Should not accept String type.", formatter.acceptType(str.referenceType(), new HashMap<>()));

        ObjectReference clz = this.getObjectReference("java.lang.Class");
        assertTrue("Should accept Class type.", formatter.acceptType(clz.referenceType(), new HashMap<>()));

        LocalVariable list = this.getLocalVariable("strList");
        assertFalse("Should not accept List type.", formatter.acceptType(list.type(), new HashMap<>()));

        LocalVariable arrays = this.getLocalVariable("arrays");
        assertFalse("Should not accept array type.", formatter.acceptType(arrays.type(), new HashMap<>()));
    }

    @Test
    public void testDefaultOptions() {
        Map<String, Object> options = formatter.getDefaultOptions();
        assertNotNull("Default options should never be null.", options);
        assertEquals("ClassObjectFormatter should have no options.", 0, options.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValueOfNotUnsupported() {
        formatter.valueOf("java.lang.String.class", getVM().mirrorOf("").referenceType(), new HashMap<>());
        fail("Set value for class is not supported.");
    }

    @Test
    public void testToString() throws Exception {
        Value clazzValue = this.getLocalValue("b");
        assertEquals("Should be able to format clazz type.", String.format("java.lang.Class (A)@%d",
            ((ObjectReference)clazzValue).uniqueID()),
            formatter.toString(clazzValue, new HashMap<>()));
    }
}
