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

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private static final long serialVersionUID = -7068164191168103891L;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private int cacheSize;

    /**
     * Create a LUR cache with the max capacity.
     * @param cacheSize the max size of elements in this cache.
     */
    public LRUCache(int cacheSize) {
        super((int) Math.ceil(cacheSize / DEFAULT_LOAD_FACTOR) + 1, DEFAULT_LOAD_FACTOR, true);
        if (cacheSize < 0) {
            throw new IllegalArgumentException("cacheSize is negative.");
        }
        this.cacheSize = cacheSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > cacheSize;
    }
}
