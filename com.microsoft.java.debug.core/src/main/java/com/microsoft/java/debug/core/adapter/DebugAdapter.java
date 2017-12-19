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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.adapter.handler.AttachRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.ConfigurationDoneRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.DisconnectRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.EvaluateRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.InitializeRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.LaunchRequestHandler;
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
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private IDebugAdapterContext debugContext = null;
    private Map<Command, List<IDebugRequestHandler>> requestHandlers = null;
    private Map<Command, List<Command>> commandDependencies = new HashMap<>();
    private Map<Command, CompletableFuture<Messages.Response>> lastResponses = new HashMap<>();

    /**
     * Constructor.
     */
    public DebugAdapter(IProtocolServer server, IProviderContext providerContext) {
        this.debugContext = new DebugAdapterContext(server, providerContext);
        requestHandlers = new HashMap<>();
        initialize();
    }

    @Override
    public CompletableFuture<Messages.Response> dispatchRequest(Messages.Request request) {
        Messages.Response response = new Messages.Response();
        response.request_seq = request.seq;
        response.command = request.command;
        response.success = true;

        Command command = Command.parse(request.command);
        List<Command> dependencies = getCommandDependencies(command);
        CompletableFuture<Object> aggregatedDependencyFuture = new CompletableFuture<>();
        if (dependencies.size() == 0) {
            aggregatedDependencyFuture.complete(null);
        } else {
            AtomicInteger unresolvedFutureCount = new AtomicInteger(dependencies.size());
            for (CompletableFuture<Messages.Response> result : getFutures(dependencies)) {
                result.whenComplete((res, err) -> {
                    if (unresolvedFutureCount.decrementAndGet() == 0) {
                        aggregatedDependencyFuture.complete(null);
                    }
                });
            }
        }

        CompletableFuture<Messages.Response> thisFuture = aggregatedDependencyFuture.thenCompose(o -> processRequest(request, response));
        setFuture(command, thisFuture);

        return thisFuture;
    }

    private CompletableFuture<Messages.Response> processRequest(Messages.Request request, Messages.Response response) {
        Command command = Command.parse(request.command);
        Arguments cmdArgs = JsonUtils.fromJson(request.arguments, command.getArgumentType());
        List<IDebugRequestHandler> handlers = requestHandlers.get(command);

        if (debugContext.isVmTerminated()) {
            // the operation is meaningless
            return CompletableFuture.completedFuture(response);
        }

        if (handlers != null && !handlers.isEmpty()) {
            CompletableFuture<Messages.Response> future = CompletableFuture.completedFuture(response);
            for (IDebugRequestHandler handler : handlers) {
                future = future.thenCompose((res) -> {
                    return handler.handle(command, cmdArgs, res, debugContext);
                });
            }
            return future;
        }

        final String errorMessage = String.format("Unrecognized request: { _request: %s }", request.command);
        logger.log(Level.SEVERE, errorMessage);
        return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE, errorMessage);
    }

    private void initialize() {
        // Register request handlers.
        // When there are multiple handlers registered for the same request, follow the rule "first register, first execute".
        registerHandler(new InitializeRequestHandler());
        registerHandler(new LaunchRequestHandler());
        registerHandler(new AttachRequestHandler());
        registerHandler(new ConfigurationDoneRequestHandler());
        registerHandler(new DisconnectRequestHandler());
        registerHandler(new SetBreakpointsRequestHandler());
        registerHandler(new SetExceptionBreakpointsRequestHandler());
        registerHandler(new SourceRequestHandler());
        registerHandler(new ThreadsRequestHandler());
        registerHandler(new StepRequestHandler());
        registerHandler(new StackTraceRequestHandler());
        registerHandler(new ScopesRequestHandler());
        registerHandler(new VariablesRequestHandler());
        registerHandler(new SetVariableRequestHandler());
        registerHandler(new EvaluateRequestHandler());

        addCommandDependency(Command.EVALUATE, Command.VARIABLES);
    }

    private void registerHandler(IDebugRequestHandler handler) {
        for (Command command : handler.getTargetCommands()) {
            List<IDebugRequestHandler> handlerList = requestHandlers.get(command);
            if (handlerList == null) {
                handlerList = new ArrayList<>();
                requestHandlers.put(command, handlerList);
            }
            handlerList.add(handler);
        }
    }

    private void addCommandDependency(Command command, Command dependency) {
        List<Command> dependencies = commandDependencies.computeIfAbsent(command, key -> new ArrayList());
        if (!dependencies.contains(dependency)) {
            dependencies.add(dependency);
        }
    }

    private List<Command> getCommandDependencies(Command command) {
        return commandDependencies.computeIfAbsent(command, key -> new ArrayList());
    }

    private CompletableFuture<Messages.Response> getFuture(Command command) {
        return lastResponses.computeIfAbsent(command, key -> CompletableFuture.completedFuture(new Messages.Response()));
    }

    private List<CompletableFuture<Messages.Response>> getFutures(List<Command> commands) {
        return commands.stream().map(cmd -> getFuture(cmd)).collect(Collectors.toList());
    }

    private void setFuture(Command command, CompletableFuture<Messages.Response> response) {
        lastResponses.put(command, response);
    }
}
