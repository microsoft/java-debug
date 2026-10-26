/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.util.Optional;

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;

final class DebuggeeProcessHelper {
    private DebuggeeProcessHelper() {
    }

    static void terminate(IDebugAdapterContext context) {
        Process debuggeeProcess = context.getDebuggeeProcess();
        if (debuggeeProcess != null) {
            debuggeeProcess.destroy();
        } else if (context.getProcessId() > 0) {
            Optional<ProcessHandle> debuggeeHandle = ProcessHandle.of(context.getProcessId());
            if (debuggeeHandle.isPresent()) {
                debuggeeHandle.get().destroy();
            }
        }
    }
}
