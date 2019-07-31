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

package com.microsoft.java.debug.core.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.adapter.handler.AttachRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.CompletionsHandler;
import com.microsoft.java.debug.core.adapter.handler.ConfigurationDoneRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.DisconnectRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.DisconnectRequestWithoutDebuggingHandler;
import com.microsoft.java.debug.core.adapter.handler.EvaluateRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.ExceptionInfoRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.HotCodeReplaceHandler;
import com.microsoft.java.debug.core.adapter.handler.InitializeRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.LaunchRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.RestartFrameHandler;
import com.microsoft.java.debug.core.adapter.handler.ScopesRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.SetBreakpointsRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.SetExceptionBreakpointsRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.SetVariableRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.SourceRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.StackTraceRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.StepRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.ThreadsRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.VariablesRequestHandler;
import com.microsoft.java.debug.core.protocol.IProtocolServer;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Messages;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;

public class DebugAdapter implements IDebugAdapter {
    private final Logger logger;

    private IDebugAdapterContext debugContext = null;
    private Map<Command, List<IDebugRequestHandler>> requestHandlersForDebug = null;
    private Map<Command, List<IDebugRequestHandler>> requestHandlersForNoDebug = null;

    /**
     * Constructor.
     */
    public DebugAdapter(IProtocolServer server, IProviderContext providerContext, Logger logger) {
        this.logger = logger;
        this.debugContext = new DebugAdapterContext(server, providerContext);
        requestHandlersForDebug = new HashMap<>();
        requestHandlersForNoDebug = new HashMap<>();
        initialize();
    }

    @Override
    public CompletableFuture<Messages.Response> dispatchRequest(Messages.Request request) {
        Messages.Response response = new Messages.Response();
        response.request_seq = request.seq;
        response.command = request.command;
        response.success = true;

        Command command = Command.parse(request.command);
        Arguments cmdArgs = JsonUtils.fromJson(request.arguments, command.getArgumentType());

        if (debugContext.isVmTerminated() && command != Command.DISCONNECT) {
            return CompletableFuture.completedFuture(response);
        }
        List<IDebugRequestHandler> handlers = this.debugContext.getLaunchMode() == LaunchMode.DEBUG
                ? requestHandlersForDebug.get(command) : requestHandlersForNoDebug.get(command);
        if (handlers != null && !handlers.isEmpty()) {
            CompletableFuture<Messages.Response> future = CompletableFuture.completedFuture(response);
            for (IDebugRequestHandler handler : handlers) {
                future = future.thenCompose((res) -> {
                    return handler.handle(command, cmdArgs, res, debugContext);
                });
            }
            return future;
        } else {
            final String errorMessage = String.format("Unrecognized request: { _request: %s }", request.command);
            logger.log(Level.SEVERE, errorMessage);
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE, errorMessage);
        }
    }

    private void initialize() {
        // Register request handlers.
        // When there are multiple handlers registered for the same request, follow the rule "first register, first execute".
        registerHandler(new InitializeRequestHandler());
        registerHandler(new LaunchRequestHandler(logger));

        // DEBUG node only
        registerHandlerForDebug(new AttachRequestHandler(logger));
        registerHandlerForDebug(new ConfigurationDoneRequestHandler(logger));
        registerHandlerForDebug(new DisconnectRequestHandler(logger));
        registerHandlerForDebug(new SetBreakpointsRequestHandler(logger));
        registerHandlerForDebug(new SetExceptionBreakpointsRequestHandler());
        registerHandlerForDebug(new SourceRequestHandler());
        registerHandlerForDebug(new ThreadsRequestHandler());
        registerHandlerForDebug(new StepRequestHandler());
        registerHandlerForDebug(new StackTraceRequestHandler());
        registerHandlerForDebug(new ScopesRequestHandler());
        registerHandlerForDebug(new VariablesRequestHandler(logger));
        registerHandlerForDebug(new SetVariableRequestHandler());
        registerHandlerForDebug(new EvaluateRequestHandler(logger));
        registerHandlerForDebug(new HotCodeReplaceHandler());
        registerHandlerForDebug(new RestartFrameHandler());
        registerHandlerForDebug(new CompletionsHandler());
        registerHandlerForDebug(new ExceptionInfoRequestHandler(logger));

        // NO_DEBUG mode only
        registerHandlerForNoDebug(new DisconnectRequestWithoutDebuggingHandler(logger));

    }

    private void registerHandlerForDebug(IDebugRequestHandler handler) {
        registerHandler(requestHandlersForDebug, handler);
    }

    private void registerHandlerForNoDebug(IDebugRequestHandler handler) {
        registerHandler(requestHandlersForNoDebug, handler);
    }

    private void registerHandler(IDebugRequestHandler handler) {
        registerHandler(requestHandlersForDebug, handler);
        registerHandler(requestHandlersForNoDebug, handler);
    }

    private void registerHandler(Map<Command, List<IDebugRequestHandler>> requestHandlers, IDebugRequestHandler handler) {
        for (Command command : handler.getTargetCommands()) {
            List<IDebugRequestHandler> handlerList = requestHandlers.get(command);
            if (handlerList == null) {
                handlerList = new ArrayList<>();
                requestHandlers.put(command, handlerList);
            }
            handler.initialize(debugContext);
            handlerList.add(handler);
        }
    }
}
