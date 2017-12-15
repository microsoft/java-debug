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

import java.util.concurrent.locks.ReentrantLock;

public class DisposableReentrantLock<T> implements IDisposable {
    private final T underlyingObject;
    private final ReentrantLock lock;

    /**
     * Create a disposable lock together with a underlying object.
     *
     * @param underlyingObject the underlying object
     * @param lock the lock
     */
    public DisposableReentrantLock(T underlyingObject, ReentrantLock lock) {
        if (lock == null) {
            throw new IllegalArgumentException("Null lock is illegal for DisposableLock.");
        }

        if (underlyingObject == null) {
            throw new IllegalArgumentException("Null underlyingObject is illegal for DisposableLock.");
        }

        this.underlyingObject = underlyingObject;
        this.lock = lock;
    }

    @Override
    public void close() {
        if (lock.isLocked()) {
            lock.unlock();
        }
    }

    public T getUnderlyingObject() {
        return underlyingObject;
    }
}
