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

package com.microsoft.java.debug.plugin.internal;

import java.util.ArrayList;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UserDataPool {
    private ConcurrentLinkedQueue<Object> queue;

    public UserDataPool() {
        queue = new ConcurrentLinkedQueue<>();
    }

    private static final class SingletonHolder {
        private static final UserDataPool INSTANCE = new UserDataPool();
    }

    /**
     * Fetch all pending user data records
     * @return List of user data Object.
     */
    public List<Object> fetchAll() {
        List<Object> ret = new ArrayList<>();
        while (!queue.isEmpty()) {
            ret.add(queue.poll());
        }
        return ret;
    }

    public static UserDataPool getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * Log user data Object into the queue.
     * @param message   Error message.
     * @param parameters    Session-based user data Objects.
     */
    public void logUserdata(String message, Object[] parameters) {
        if (queue == null) {
            return;
        }
        if (parameters != null) {
            for (Object entry : parameters) {
                queue.add(entry);
            }
        } else {
            queue.add(message);
        }
    }
}
