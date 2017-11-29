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

import java.util.function.Consumer;

import com.microsoft.java.debug.core.protocol.Events.DebugEvent;
import com.microsoft.java.debug.core.protocol.Messages.Request;
import com.microsoft.java.debug.core.protocol.Messages.Response;

public interface IProtocolServer {
    /**
     * Send a request to the DA.
     *
     * @param request
     *            the request message.
     * @param cb
     *            the request call back function.
     */
    void sendRequest(Request request, Consumer<Response> cb);

    /**
     * Send a request to the DA. And create a timeout error response to the callback if no response is received at the give time.
     *
     * @param request
     *            the request message.
     * @param timeout
     *            the maximum time (in millis) to wait.
     * @param cb
     *            the request call back function.
     */
    void sendRequest(Request request, long timeout, Consumer<Response> cb);

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
