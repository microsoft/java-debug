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


import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LRUCacheTest extends EasyMockSupport {
    private LRUCache cache;
    @Before
    public void setup() {
        cache = new LRUCache<String, Integer>(100);
    }

    @Test
    public void testLRUCacheCtor() throws Exception {
        try {
            new LRUCache(-1);
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            new LRUCache(0);
        } catch (IllegalArgumentException ex) {
            // expected
        }
        new LRUCache(10);
    }

    @Test
    public void testLRUCache() throws Exception {
        assertEquals("Should be initial empty.", 0, cache.size());
        assertEquals("Should be initial empty.", true, cache.isEmpty());

        for (int i = 0 ; i < 100; i++) {
            cache.put(String.valueOf(i), i);
            assertEquals("Should be the right size.", i+1, cache.size());
        }

        for (int i = 100 ; i < 20000000; i++) {
            cache.put(String.valueOf(i), i);
        }

        assertEquals("Should keep latest element.", 20000000 - 1, cache.get(String.valueOf(20000000-1)));

        assertFalse("Should not keep the obsolete element.",
            cache.containsKey(String.valueOf(20000000 - 101)));
    }
}
