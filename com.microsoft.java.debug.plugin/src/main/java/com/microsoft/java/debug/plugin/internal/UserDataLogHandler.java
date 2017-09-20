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

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class UserDataLogHandler extends Handler {
    Level thresholdLevel = Level.SEVERE;

    public UserDataLogHandler(Level level) {
        thresholdLevel = level;
    }

    @Override
    public void publish(LogRecord record) {
        if (record.getLevel().intValue() >= thresholdLevel.intValue()) {
            UserDataPool.getInstance().logUserdata(record.getMessage(), record.getParameters());
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

}
