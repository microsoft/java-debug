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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import com.microsoft.java.debug.core.adapter.handler.ThreadsRequestHandler;
import com.microsoft.java.debug.core.adapter.handler.VariablesRequestHandler;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Events.ExitedEvent;
import com.microsoft.java.debug.core.protocol.Events.TerminatedEvent;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Messages;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;

public class DebugAdapter implements IDebugAdapter {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private BiConsumer<Events.DebugEvent, Boolean> eventConsumer;
    private IProviderContext providerContext;
    private IDebugAdapterContext debugContext = null;
    private Map<Command, List<IDebugRequestHandler>> requestHandlers = null;

    /**
     * Constructor.
     */
    public DebugAdapter(BiConsumer<Events.DebugEvent, Boolean> consumer, Consumer<Response> sendMessageFunc, IProviderContext providerContext) {
        eventConsumer = consumer;
        this.providerContext = providerContext;
        debugContext = new DebugAdapterContext(this);
        debugContext.setResponseConsumer(sendMessageFunc);
        requestHandlers = new HashMap<>();
        initialize();
    }

    @Override
    public Messages.Response dispatchRequest(Messages.Request request) {
        Messages.Response response = new Messages.Response();
        response.request_seq = request.seq;
        response.command = request.command;
        response.success = true;

        Command command = Command.parse(request.command);
        Arguments cmdArgs = JsonUtils.fromJson(request.arguments, command.getArgumentType());

        try {
            if (debugContext.isVmTerminated()) {
                // the operation is meaningless
                return response;
            }
            List<IDebugRequestHandler> handlers = requestHandlers.get(command);
            debugContext.setResponseAsync(false);
            if (handlers != null && !handlers.isEmpty()) {
                for (IDebugRequestHandler handler : handlers) {
                    handler.handle(command, cmdArgs, response, debugContext);
                }
            } else {
                AdapterUtils.setErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE,
                        String.format("Unrecognized request: { _request: %s }", request.command));
            }
            if (debugContext.shouldSendResponseAsync()) {
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("DebugSession dispatch exception: %s", e.toString()), e);
            AdapterUtils.setErrorResponse(response, ErrorCode.UNKNOWN_FAILURE,
                    e.getMessage() != null ? e.getMessage() : e.toString());
        }

        return response;
    }

    /**
     * Send event to DA immediately.
     *
     * @see ProtocolServer#sendEvent(String, Object)
     */
    public void sendEvent(Events.DebugEvent event) {
        if (debugContext.isVmTerminated() && !(event instanceof TerminatedEvent || event instanceof ExitedEvent)) {
            // the operation is meaningless
            return;
        }
        eventConsumer.accept(event, false);
    }

    /**
     * Send event to DA after the current dispatching request is resolved.
     *
     * @see ProtocolServer#sendEventLater(String, Object)
     */
    public void sendEventLater(Events.DebugEvent event) {
        if (debugContext.isVmTerminated() && !(event instanceof TerminatedEvent || event instanceof ExitedEvent)) {
            // the operation is meaningless
            return;
        }
        eventConsumer.accept(event, true);
    }

    public <T extends IProvider> T getProvider(Class<T> clazz) {
        return providerContext.getProvider(clazz);
    }

    private void initialize() {
        // Register request handlers.
        registerHandler(new InitializeRequestHandler());
        registerHandler(new LaunchRequestHandler());
        registerHandler(new AttachRequestHandler());
        registerHandler(new ConfigurationDoneRequestHandler());
        registerHandler(new DisconnectRequestHandler());
        registerHandler(new SetBreakpointsRequestHandler());
        registerHandler(new SetExceptionBreakpointsRequestHandler());
        registerHandler(new SourceRequestHandler());
        registerHandler(new ThreadsRequestHandler());
        registerHandler(new StackTraceRequestHandler());
        registerHandler(new ScopesRequestHandler());
        registerHandler(new VariablesRequestHandler());
        registerHandler(new SetVariableRequestHandler());
        registerHandler(new EvaluateRequestHandler());
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
}