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

public class DisposableLock implements IDisposable {
    private ReentrantLock lock;

    /**
     * Create a disposable lock object.
     *
     * @param lock the lock
     */
    public DisposableLock(ReentrantLock lock) {
        if (lock == null) {
            throw new IllegalArgumentException("Null lock is illegal for LockedObject.");
        }

        this.lock = lock;
    }

    @Override
    public void close() {
        if (lock != null) {
            if (lock.isLocked()) {
                lock.unlock();
            }
            lock = null;
        }
    }
}
