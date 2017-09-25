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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

class JdtLogHandler extends Handler {

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public void flush() {
        // do nothing
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        int severity = IStatus.INFO;
        if (record.getLevel() == Level.SEVERE) {
            severity = IStatus.ERROR;
        } else if (record.getLevel() == Level.WARNING) {
            severity = IStatus.WARNING;
        }

        IStatus status = new Status(severity, record.getLoggerName(), record.getMessage(), record.getThrown());

        Platform.getLog(JavaDebuggerServerPlugin.context.getBundle()).log(status);
    }
}