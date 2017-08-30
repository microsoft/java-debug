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

package org.eclipse.jdt.ls.debug.adapter;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IdCollection<T> {
    private int startId;
    private AtomicInteger nextId;
    private HashMap<Integer, T> idMap;
    private HashMap<T, Integer> reverseMap;

    public IdCollection() {
        this(1);
    }

    /**
     * Constructs a new id generator with the given startId as the start id number.
     * @param startId
     *              the start id number
     */
    public IdCollection(int startId) {
        this.startId = startId;
        this.nextId = new AtomicInteger(startId);
        this.idMap = new HashMap<>();
        this.reverseMap = new HashMap<>();
    }

    /**
     * Reset the id to the initial start number.
     */
    public void reset() {
        this.nextId.set(this.startId);
        this.idMap.clear();
        this.reverseMap.clear();
    }

    /**
     * Create a new id if the id doesn't exist for the given value.
     * Otherwise return the existing id. 
     */
    public int create(T value) {
        if (this.reverseMap.containsKey(value)) {
            return this.reverseMap.get(value);
        }
        int id = this.nextId.getAndIncrement();
        this.idMap.put(id, value);
        this.reverseMap.put(value, id);
        return id;
    }

    /**
     * Get the original value by the id.
     */
    public T get(int id) {
        return this.idMap.get(id);
    }

    /**
     * Remove the id from the id collection.
     */
    public T remove(int id) {
        T target = this.idMap.remove(id);
        if (target != null) {
            this.reverseMap.remove(target);
        }
        return target;
    }
}
