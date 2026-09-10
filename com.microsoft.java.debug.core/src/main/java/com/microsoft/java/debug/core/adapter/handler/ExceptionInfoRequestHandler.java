/*******************************************************************************
* Copyright (c) 2019-2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.JdiExceptionReference;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.ExceptionInfoArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types.ExceptionBreakMode;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;

public class ExceptionInfoRequestHandler implements IDebugRequestHandler {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.EXCEPTIONINFO);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        ExceptionInfoArguments exceptionInfoArgs = (ExceptionInfoArguments) arguments;
        ThreadReference thread = context.getThreadCache().getThread(exceptionInfoArgs.threadId);
        if (thread == null) {
            thread = DebugUtility.getThread(context.getDebugSession(), exceptionInfoArgs.threadId);
        }

        if (thread == null) {
            throw AdapterUtils.createCompletionException("Thread " + exceptionInfoArgs.threadId + " doesn't exist.", ErrorCode.EXCEPTION_INFO_FAILURE);
        }

        JdiExceptionReference jdiException = context.getExceptionManager().getException(exceptionInfoArgs.threadId);
        if (jdiException == null) {
            throw AdapterUtils.createCompletionException("No exception exists in thread " + exceptionInfoArgs.threadId, ErrorCode.EXCEPTION_INFO_FAILURE);
        }

        Method toStringMethod = null;
        for (Method method : jdiException.exception.referenceType().allMethods()) {
            if (Objects.equals("toString", method.name()) && Objects.equals("()Ljava/lang/String;", method.signature())) {
                toStringMethod = method;
                break;
            }
        }

        String typeName = jdiException.exception.type().name();
        String exceptionToString = typeName;
        if (toStringMethod != null) {
            try {
                Value returnValue = jdiException.exception.invokeMethod(thread, toStringMethod, Collections.emptyList(), ObjectReference.INVOKE_SINGLE_THREADED);
                exceptionToString = returnValue.toString();
            } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException
                    | InvocationException e) {
                logger.log(Level.SEVERE, String.format("Failed to get the return value of the method Exception.toString(): %s", e.toString(), e));
            } finally {
                try {
                    // See bug https://github.com/microsoft/vscode-java-debug/issues/767:
                    // The operation exception.invokeMethod above will resume the thread, that will cause
                    // the previously cached stack frames for this thread to be invalid.
                    context.getStackFrameManager().reloadStackFrames(thread);
                } catch (Exception e) {
                    // do nothing.
                }
            }
        }

        response.body = new Responses.ExceptionInfoResponse(typeName, exceptionToString,
                jdiException.isUncaught ? ExceptionBreakMode.USERUNHANDLED : ExceptionBreakMode.ALWAYS);
        return CompletableFuture.completedFuture(response);
    }
}
