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

package org.eclipse.jdt.ls.debug.adapter.handler;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.ls.debug.DebugUtility;
import org.eclipse.jdt.ls.debug.adapter.AdapterUtils;
import org.eclipse.jdt.ls.debug.adapter.IDebugAdapterContext;
import org.eclipse.jdt.ls.debug.adapter.IDebugRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.ISourceLookUpProvider;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.Requests.StackTraceArguments;
import org.eclipse.jdt.ls.debug.adapter.Responses;
import org.eclipse.jdt.ls.debug.adapter.Types;
import org.eclipse.jdt.ls.debug.adapter.variables.JdiObjectProxy;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public class StackTraceRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.STACKTRACE);
    }

    @Override
    public void handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        StackTraceArguments stacktraceArgs = (StackTraceArguments) arguments;
        List<Types.StackFrame> result = new ArrayList<>();
        if (stacktraceArgs.startFrame < 0 || stacktraceArgs.levels < 0) {
            response.body = new Responses.StackTraceResponseBody(result, 0);
            return;
        }
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), stacktraceArgs.threadId);
        int totalFrames = 0;
        if (thread != null) {
            try {
                totalFrames = thread.frameCount();
                if (totalFrames <= stacktraceArgs.startFrame) {
                    response.body = new Responses.StackTraceResponseBody(result, totalFrames);
                    return;
                }
                List<StackFrame> stackFrames = stacktraceArgs.levels == 0
                        ? thread.frames(stacktraceArgs.startFrame, totalFrames - stacktraceArgs.startFrame)
                        : thread.frames(stacktraceArgs.startFrame,
                        Math.min(totalFrames - stacktraceArgs.startFrame, stacktraceArgs.levels));
                for (int i = 0; i < stackFrames.size(); i++) {
                    StackFrame stackFrame = stackFrames.get(i);
                    int frameId = context.getRecyclableIdPool().addObject(stackFrame.thread().uniqueID(),
                            new JdiObjectProxy<>(stackFrame));
                    Types.StackFrame clientStackFrame = convertDebuggerStackFrameToClient(stackFrame, frameId, context);
                    result.add(clientStackFrame);
                }
            } catch (IncompatibleThreadStateException | IndexOutOfBoundsException | URISyntaxException | AbsentInformationException e) {
                // do nothing.
            }
        }
        response.body = new Responses.StackTraceResponseBody(result, totalFrames);
    }

    private Types.StackFrame convertDebuggerStackFrameToClient(StackFrame stackFrame, int frameId, IDebugAdapterContext context)
            throws URISyntaxException, AbsentInformationException {
        Location location = stackFrame.location();
        Method method = location.method();
        Types.Source clientSource = this.convertDebuggerSourceToClient(location, context);
        String methodName = method.name();
        int lineNumber = AdapterUtils.convertLineNumber(location.lineNumber(), context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
        if (lineNumber < 0 && method.isNative()) {
            // When the current stack frame stops at a native method, the line number is -1.
            // Display a tip text "native method" in the Call Stack View.
            methodName += "[native method]";
        }
        return new Types.StackFrame(frameId, methodName, clientSource, lineNumber, 0);
    }

    private Types.Source convertDebuggerSourceToClient(Location location, IDebugAdapterContext context) throws URISyntaxException {
        final String fullyQualifiedName = location.declaringType().name();
        String sourceName = "";
        String relativeSourcePath = "";
        try {
            // When the .class file doesn't contain source information in meta data,
            // invoking Location#sourceName() would throw AbsentInformationException.
            sourceName = location.sourceName();
            relativeSourcePath = location.sourcePath();
        } catch (AbsentInformationException e) {
            String enclosingType = AdapterUtils.parseEnclosingType(fullyQualifiedName);
            sourceName = enclosingType.substring(enclosingType.lastIndexOf('.') + 1) + ".java";
            relativeSourcePath = enclosingType.replace('.', '/') + ".java";
        }

        final String finalRelativeSourcePath = relativeSourcePath;
        // use a lru cache for better performance
        String uri = context.getSourceLookupCache().computeIfAbsent(fullyQualifiedName, key ->
            context.getProvider(ISourceLookUpProvider.class).getSourceFileURI(key, finalRelativeSourcePath)
        );

        if (uri != null) {
            String clientPath = AdapterUtils.convertPath(uri, context.isDebuggerPathsAreUri(), context.isClientPathsAreUri());
            if (uri.startsWith("file:")) {
                return new Types.Source(sourceName, clientPath, 0);
            } else {
                return new Types.Source(sourceName, clientPath, context.createSourceReference(uri));
            }
        } else {
            // If the source lookup engine cannot find the source file, then lookup it in the source directories specified by user.
            String absoluteSourcepath = AdapterUtils.sourceLookup(context.getSourcePath(), relativeSourcePath);
            return new Types.Source(sourceName, absoluteSourcepath, 0);
        }
    }
}
