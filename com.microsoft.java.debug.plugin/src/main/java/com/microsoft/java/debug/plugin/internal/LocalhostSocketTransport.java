/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.microsoft.java.debug.plugin.internal;

import java.io.IOException;

import org.eclipse.jdi.internal.connect.SocketTransportService;

import com.sun.jdi.connect.Transport;
import com.sun.jdi.connect.spi.Connection;
import com.sun.jdi.connect.spi.TransportService.ListenKey;

public class LocalhostSocketTransport implements Transport {
    public static final String TRANSPORT_NAME = "dt_socket"; //$NON-NLS-1$
    public static final int MIN_PORTNR = 0;
    public static final int MAX_PORTNR = 65535;

    LocalhostSocketTransportService service;
    private ListenKey fListenKey;

    /**
     * Constructs new LocalhostSocketTransport.
     */
    public LocalhostSocketTransport() {
        service = new LocalhostSocketTransportService();
    }

    /*
     * (non-Javadoc)
     *
     * @see com.sun.jdi.connect.Transport#name()
     */
    @Override
    public String name() {
        return TRANSPORT_NAME;
    }

    public Connection attach(String hostname, int port, long attachTimeout,
            long handshakeTimeout) throws IOException {
        return service.attach(hostname, port, attachTimeout, handshakeTimeout);
    }

    public String startListening(int port) throws IOException {
        fListenKey = service.startListening("localhost:" + port); //$NON-NLS-1$
        return fListenKey.address();
    }

    public void stopListening() throws IOException {
        service.stopListening(fListenKey);
    }

    public Connection accept(long attachTimeout, long handshakeTimeout)
            throws IOException {
        return service.accept(fListenKey, attachTimeout, handshakeTimeout);
    }

}
