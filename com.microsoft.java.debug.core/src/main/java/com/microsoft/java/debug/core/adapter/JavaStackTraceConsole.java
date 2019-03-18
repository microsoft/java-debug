/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import java.io.File;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.adapter.handler.StackTraceRequestHandler;
import com.microsoft.java.debug.core.protocol.Events.OutputEvent;
import com.microsoft.java.debug.core.protocol.Types;

public class JavaStackTraceConsole {
    private static final Pattern stacktracePattern = Pattern.compile("\\s+at\\s+(([\\w$]+\\.)*[\\w$]+)\\(([\\w-$]+\\.java:\\d+)\\)");
    private ProcessConsole console;
    private IDebugAdapterContext context;

    public JavaStackTraceConsole(ProcessConsole console, IDebugAdapterContext context) {
        this.console = console;
        this.context = context;
    }

    /**
     * Start listening on the process console.
     */
    public void start() {
        String[] lastIncompleteLines = new String[] {
            null,
            null
        };
        this.console.onStdout((message) -> {
            lastIncompleteLines[0] = process(message, lastIncompleteLines[0], OutputEvent.Category.stdout);
        });

        this.console.onStderr((message) -> {
            lastIncompleteLines[1] = process(message, lastIncompleteLines[1], OutputEvent.Category.stderr);
        });

        this.console.start();
    }

    private String process(String message, String lastIncompleteLine, OutputEvent.Category category) {
        String[] lines = message.split("\n");
        boolean endWithLF = message.endsWith("\n");
        String unsent = "";
        for (int i = 0; i < lines.length; i++) {
            String toSend = (i < lines.length - 1 || endWithLF) ? lines[i] + "\n" : lines[i];
            String revisedLine = lines[i];
            if (lastIncompleteLine != null) {
                revisedLine = lastIncompleteLine + revisedLine;
                lastIncompleteLine = null;
            }

            Matcher matcher = stacktracePattern.matcher(revisedLine);
            if (matcher.find()) {
                if (StringUtils.isNotEmpty(unsent)) {
                    context.getProtocolServer().sendEvent(new OutputEvent(category, unsent));
                    unsent = "";
                }

                String methodField = matcher.group(1);
                String locationField = matcher.group(matcher.groupCount());

                String fullyQualifiedName = methodField.substring(0, methodField.lastIndexOf("."));
                String packageName = fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf("."));
                String[] locations = locationField.split(":");
                String sourceName = locations[0];
                int lineNumber = Integer.parseInt(locations[1]);
                String sourcePath = StringUtils.isBlank(packageName) ? sourceName
                        : packageName.replace('.', File.separatorChar) + File.separatorChar + sourceName;
                Types.Source source = null;
                try {
                    source = StackTraceRequestHandler.convertDebuggerSourceToClient(fullyQualifiedName, sourceName, sourcePath, context);
                } catch (URISyntaxException e) {
                    // do nothing.
                }

                context.getProtocolServer().sendEvent(new OutputEvent(category, toSend, source, lineNumber));
            } else {
                unsent += toSend;
            }

            if (i == lines.length - 1 && StringUtils.isNotEmpty(unsent)) {
                context.getProtocolServer().sendEvent(new OutputEvent(category, unsent));
                if (!endWithLF) {
                    lastIncompleteLine = toSend;
                }
            }
        }

        return lastIncompleteLine;
    }
}
