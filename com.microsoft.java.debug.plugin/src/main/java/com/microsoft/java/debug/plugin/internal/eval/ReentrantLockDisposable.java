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

package com.microsoft.java.debug.plugin.internal.eval;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.microsoft.java.debug.core.adapter.IDisposable;

class ReentrantLockDisposable implements IDisposable {
    private final Lock lock;

    ReentrantLockDisposable() {
        lock =  new ReentrantLock();
        lock.lock();
    }

    @Override
    public void close() {
        lock.unlock();
    }
}
