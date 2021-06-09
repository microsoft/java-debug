/*******************************************************************************
 * Copyright (c) 2017-2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.eclipse.jdi.internal.VirtualMachineManagerImpl;
import org.eclipse.jdi.internal.connect.ConnectMessages;
import org.eclipse.jdi.internal.connect.SocketListeningConnectorImpl;
import org.eclipse.jdi.internal.connect.SocketTransportImpl;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;

/**
 * An advanced launching connector that supports cwd and enviroment variables.
 *
 */
public class AdvancedListeningConnector extends SocketListeningConnectorImpl implements ListeningConnector {
    private static final int ACCEPT_TIMEOUT = 10 * 1000;

    public AdvancedListeningConnector(VirtualMachineManagerImpl virtualMachineManager) {
        super(virtualMachineManager);

        // Create communication protocol specific transport.
        this.fTransport = new LocalhostSocketTransport();
    }

    @Override
    public String name() {
        return "com.microsoft.java.debug.AdvancedListeningConnector";
    }

    /**
     * Retrieves connection port.
     */
    private int getConnectionPort(Map<String, ? extends Connector.Argument> connectionArgs) throws IllegalConnectorArgumentsException {
        String attribute = "port"; //$NON-NLS-1$
        try {
            // If listening port is not specified, use port 0
            IntegerArgument argument = (IntegerArgument) connectionArgs
                    .get(attribute);
            if (argument != null && argument.value() != null) {
                return argument.intValue();
            } else {
                return 0;
            }
        } catch (ClassCastException e) {
            throw new IllegalConnectorArgumentsException(
                    ConnectMessages.SocketListeningConnectorImpl_Connection_argument_is_not_of_the_right_type_6,
                    attribute);
        } catch (NullPointerException e) {
            throw new IllegalConnectorArgumentsException(
                    ConnectMessages.SocketListeningConnectorImpl_Necessary_connection_argument_is_null_7,
                    attribute);
        } catch (NumberFormatException e) {
            throw new IllegalConnectorArgumentsException(
                    ConnectMessages.SocketListeningConnectorImpl_Connection_argument_is_not_a_number_8,
                    attribute);
        }
    }

    /**
     * Listens for one or more connections initiated by target VMs.
     *
     * @return Returns the address at which the connector is listening for a
     *         connection.
     */
    @Override
    public String startListening(Map<String, ? extends Connector.Argument> connectionArgs) throws IOException, IllegalConnectorArgumentsException {
        int port = getConnectionPort(connectionArgs);
        String result = null;
        try {
            result = ((LocalhostSocketTransport) fTransport).startListening(port);
        } catch (IllegalArgumentException e) {
            throw new IllegalConnectorArgumentsException(
                    ConnectMessages.SocketListeningConnectorImpl_ListeningConnector_Socket_Port,
                    "port"); //$NON-NLS-1$
        }
        return result;
    }

    /* (non-Javadoc)
     * @see com.sun.jdi.connect.ListeningConnector#stopListening(java.util.Map)
     */
    @Override
    public void stopListening(Map<String, ? extends Connector.Argument> connectionArgs) throws IOException {
        ((LocalhostSocketTransport) fTransport).stopListening();
    }

    /**
     * Waits for a target VM to attach to this connector.
     *
     * @return Returns a connected Virtual Machine.
     */
    @Override
    public VirtualMachine accept(Map<String, ? extends Connector.Argument> connectionArgs) throws IOException, IllegalConnectorArgumentsException {
        LocalhostSocketConnection connection = (LocalhostSocketConnection) ((LocalhostSocketTransport) fTransport)
                .accept(ACCEPT_TIMEOUT, 0);
        return establishedConnection(connection);
    }
}
