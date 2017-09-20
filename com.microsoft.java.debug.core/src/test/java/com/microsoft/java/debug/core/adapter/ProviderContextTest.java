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

package com.microsoft.java.debug.core.adapter;

import org.easymock.EasyMockRule;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

interface IMockProvider extends IProvider {

}

interface IMockProvider2 extends IProvider {

}

public class ProviderContextTest extends EasyMockSupport {
    @Rule
    public EasyMockRule rule = new EasyMockRule(this);

    @Mock
    private IMockProvider mockProvider;

    private Class mockInterface;

    private ProviderContext context;

    @Before
    public void setup() {
        context = new ProviderContext();
        mockInterface = IMockProvider.class;
    }

    @Test
    public void testProviderContext() throws Exception {
        try {
            context.getProvider(mockInterface);
            fail("Should throw IllegalArgumentException for get un-registered provider.");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            context.registerProvider(mockInterface, null);
            fail("Should throw IllegalArgumentException for registering null.");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            context.registerProvider(null, mockProvider);
            fail("Should throw IllegalArgumentException for registering null type.");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            context.registerProvider(mockProvider.getClass(), mockProvider);
            fail("Should throw IllegalArgumentException for registering class type.");
        } catch (IllegalArgumentException ex) {
            // expected
        }


        try {
            context.registerProvider(IMockProvider2.class, mockProvider);
            fail("Should throw IllegalArgumentException for registering un-compatible provider.");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        context.registerProvider(mockInterface, mockProvider);

        try {
            context.registerProvider(mockInterface, mockProvider);
            fail("Should throw IllegalArgumentException for duplicate registration.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        assertEquals("Should get the registered provider", context.getProvider(mockInterface), mockProvider);
        assertEquals("Should get the registered provider", context.getProvider(mockInterface), mockProvider);
    }


}
