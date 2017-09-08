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

package org.eclipse.jdt.ls.debug.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.handler.AttachRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.ConfigurationDoneRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.DisconnectRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.EvaluateRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.InitializeRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.LaunchRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.ScopesRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.SetBreakpointsRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.SetExceptionBreakpointsRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.SetVariableRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.SourceRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.StackTraceRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.ThreadsRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.handler.VariablesRequestHandler;
import org.eclipse.jdt.ls.debug.internal.Logger;

public class DebugAdapter implements IDebugAdapter {
    private BiConsumer<Events.DebugEvent, Boolean> eventConsumer;
    private IProviderContext providerContext;
    private IDebugAdapterContext debugContext = null;
    private Map<Command, List<IDebugRequestHandler>> requestHandlers = null;

    /**
     * Constructor.
     */
    public DebugAdapter(BiConsumer<Events.DebugEvent, Boolean> consumer, IProviderContext providerContext) {
        this.eventConsumer = consumer;
        this.providerContext = providerContext;
        this.debugContext = new DebugAdapterContext(this);
        this.requestHandlers = new HashMap<>();
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
            List<IDebugRequestHandler> handlers = requestHandlers.get(command);
            if (handlers != null && !handlers.isEmpty()) {
                for (IDebugRequestHandler handler : handlers) {
                    handler.handle(command, cmdArgs, response, this.debugContext);
                }
            } else {
                AdapterUtils.setErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE,
                        String.format("Unrecognized request: { _request: %s }", request.command));
            }
        } catch (Exception e) {
            Logger.logException("DebugSession dispatch exception", e);
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
        this.eventConsumer.accept(event, false);
    }

    /**
     * Send event to DA after the current dispatching request is resolved.
     *
     * @see ProtocolServer#sendEventLater(String, Object)
     */
    public void sendEventLater(Events.DebugEvent event) {
        this.eventConsumer.accept(event, true);
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