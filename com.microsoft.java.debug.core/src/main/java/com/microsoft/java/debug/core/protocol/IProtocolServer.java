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

package com.microsoft.java.debug.core.protocol;

import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.protocol.Events.DebugEvent;
import com.microsoft.java.debug.core.protocol.Messages.Request;
import com.microsoft.java.debug.core.protocol.Messages.Response;

public interface IProtocolServer {
    /**
     * Send a request to the DA.
     *
     * @param request
     *            the request message.
     * @return a CompletableFuture.
     */
    CompletableFuture<Response> sendRequest(Request request);

    /**
     * Send a request to the DA. The future will complete exceptionally if no response is received at the give time.
     *
     * @param request
     *            the request message.
     * @param timeout
     *            the maximum time (in millis) to wait.
     * @return a CompletableFuture.
     */
    CompletableFuture<Response> sendRequest(Request request, long timeout);

    /**
     * Send an event to the DA.
     * @param event
     *              the event message.
     */
    void sendEvent(DebugEvent event);

    /**
     * Send a response to the DA.
     * @param response
     *                  the response message.
     */
    void sendResponse(Response response);
}
