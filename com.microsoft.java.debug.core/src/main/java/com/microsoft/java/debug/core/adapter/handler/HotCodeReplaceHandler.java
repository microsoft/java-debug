/*******************************************************************************
* Copyright (c) 2017-2019 Microsoft Corporation and others.
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.adapter.HotCodeReplaceEvent;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Events.HotCodeReplaceEvent.ChangeType;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Responses;

public class HotCodeReplaceHandler implements IDebugRequestHandler {
    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Requests.Command.REDEFINECLASSES);
    }

    @Override
    public void initialize(IDebugAdapterContext context) {
        IDebugRequestHandler.super.initialize(context);
        IHotCodeReplaceProvider provider = context.getProvider(IHotCodeReplaceProvider.class);
        provider.getEventHub()
            .subscribe(event -> {
                if (event.getEventType() == HotCodeReplaceEvent.EventType.BUILD_COMPLETE) {
                    context.getProtocolServer().sendEvent(new Events.HotCodeReplaceEvent(ChangeType.BUILD_COMPLETE, event.getMessage()));
                } else if (event.getEventType() == HotCodeReplaceEvent.EventType.STARTING) {
                    context.getProtocolServer().sendEvent(new Events.HotCodeReplaceEvent(ChangeType.STARTING, event.getMessage()));
                } else if (event.getEventType() == HotCodeReplaceEvent.EventType.END) {
                    context.getProtocolServer().sendEvent(new Events.HotCodeReplaceEvent(ChangeType.END, event.getMessage()));
                } else if (event.getEventType() == HotCodeReplaceEvent.EventType.ERROR) {
                    context.getProtocolServer().sendEvent(new Events.HotCodeReplaceEvent(ChangeType.ERROR, event.getMessage()));
                } else if (event.getEventType() == HotCodeReplaceEvent.EventType.WARNING) {
                    context.getProtocolServer().sendEvent(new Events.HotCodeReplaceEvent(ChangeType.WARNING, event.getMessage()));
                }
            });
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {

        IHotCodeReplaceProvider provider = context.getProvider(IHotCodeReplaceProvider.class);

        return provider.redefineClasses().thenApply(classNames -> {
            response.body = new Responses.RedefineClassesResponse(classNames.toArray(new String[0]));
            return response;
        });
    }

}