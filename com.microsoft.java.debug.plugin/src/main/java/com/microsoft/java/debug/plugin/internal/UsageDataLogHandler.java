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

import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.UsageDataStore;

public class UsageDataLogHandler extends Handler {
    Level thresholdLevel = Level.SEVERE;

    public UsageDataLogHandler(Level level) {
        thresholdLevel = level;
    }

    @Override
    public void publish(LogRecord record) {
        if (record.getLevel().intValue() >= thresholdLevel.intValue()) {
            if (record.getThrown() != null) {
                if (isUserError(record.getThrown())) {
                    return;
                }
                UsageDataStore.getInstance().logErrorData(record.getMessage(), record.getThrown());
                UsageDataSession.enableJdiEventSequence();
            } else if (record.getParameters() != null) {
                // debug session details
                Object[] params = record.getParameters();
                if (params.length == 1 && params[0].getClass() != null
                        && Map.class.isAssignableFrom(params[0].getClass())) {
                    UsageDataStore.getInstance().logSessionData(record.getMessage(), (Map<String, String>) params[0]);
                }
            }
        }
    }

    @Override
    public void flush() {
        // do nothing
    }

    @Override
    public void close() throws SecurityException {
        // do nothing
    }

    private static boolean isUserError(Throwable th) {
        return th instanceof DebugException &&  ((DebugException) th).isUserError();
    }
}
