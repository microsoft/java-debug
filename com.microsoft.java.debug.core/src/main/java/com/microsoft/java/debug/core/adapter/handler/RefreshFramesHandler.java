/*******************************************************************************
* Copyright (c) 2023 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.AsyncJdwpUtils;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Events.StoppedEvent;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.RefreshFramesArguments;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;

public class RefreshFramesHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.REFRESHFRAMES);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        RefreshFramesArguments refreshArgs = (RefreshFramesArguments) arguments;
        String[] affectedRootPaths = refreshArgs == null ? null : refreshArgs.affectedRootPaths;
        List<Long> pausedThreads = getPausedThreads(context);
        for (long threadId : pausedThreads) {
            if (affectedRootPaths == null || affectedRootPaths.length == 0) {
                refreshFrames(threadId, context);
                continue;
            }

            Set<String> decompiledClasses = context.getThreadCache().getDecompiledClassesByThread(threadId);
            if (decompiledClasses == null || decompiledClasses.isEmpty()) {
                continue;
            }

            if (anyInAffectedRootPaths(decompiledClasses, affectedRootPaths)) {
                refreshFrames(threadId, context);
            }
        }

        return CompletableFuture.completedFuture(response);
    }

    List<Long> getPausedThreads(IDebugAdapterContext context) {
        List<Long> results = new ArrayList<>();
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        List<ThreadReference> threads = context.getThreadCache().visibleThreads(context);
        for (ThreadReference thread : threads) {
            if (context.asyncJDWP()) {
                futures.add(AsyncJdwpUtils.supplyAsync(() -> {
                    try {
                        if (thread.isSuspended()) {
                            return thread.uniqueID();
                        }
                    } catch (ObjectCollectedException ex) {
                        // Ignore it if the thread is garbage collected.
                    }

                    return -1L;
                }));
            } else {
                try {
                    if (thread.isSuspended()) {
                        results.add(thread.uniqueID());
                    }
                } catch (ObjectCollectedException ex) {
                    // Ignore it if the thread is garbage collected.
                }
            }
        }

        List<Long> awaitedResutls = AsyncJdwpUtils.await(futures);
        for (Long threadId : awaitedResutls) {
            if (threadId > 0) {
                results.add(threadId);
            }
        }

        return results;
    }

    /**
     * See https://github.com/microsoft/vscode/issues/188606,
     * VS Code doesn't provide a simple way to refetch the stack frames.
     * We're going to resend a thread stopped event to trick the client
     * into refetching the thread stack frames.
     */
    void refreshFrames(long threadId, IDebugAdapterContext context) {
        StoppedEvent stoppedEvent = new StoppedEvent(context.getThreadCache().getThreadStoppedReason(threadId), threadId);
        stoppedEvent.preserveFocusHint = true;
        context.getProtocolServer().sendEvent(stoppedEvent);
    }

    boolean anyInAffectedRootPaths(Collection<String> classes, String[] affectedRootPaths) {
        if (affectedRootPaths == null || affectedRootPaths.length == 0) {
            return true;
        }

        for (String classUri : classes) {
            // decompiled class uri is like 'jdt://contents/rt.jar/java.io/PrintStream.class?=1.helloworld/%5C/usr%5C/lib%5C/jvm%5C/
            // java-8-oracle%5C/jre%5C/lib%5C/rt.jar%3Cjava.io(PrintStream.class'.
            if (classUri.startsWith("jdt://contents/")) {
                String jarName = classUri.substring("jdt://contents/".length());
                int sep = jarName.indexOf("/");
                jarName = sep >= 0 ? jarName.substring(0, sep) : jarName;
                for (String affectedRootPath : affectedRootPaths) {
                    if (affectedRootPath.endsWith("/" + jarName) || affectedRootPath.endsWith("\\" + jarName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
