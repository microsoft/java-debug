/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
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
 *     Ivan Popov - Bug 184211: JDI connectors throw NullPointerException if used separately
 *              from Eclipse
 *     Google Inc - add support for accepting multiple connections
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdi.internal.VirtualMachineManagerImpl;
import org.eclipse.jdi.internal.connect.SocketListeningConnectorImpl;
import org.eclipse.jdi.internal.connect.ConnectMessages;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;

public class LocalhostSocketListeningConnector extends SocketListeningConnectorImpl implements ListeningConnector {
    public LocalhostSocketListeningConnector(
            VirtualMachineManagerImpl virtualMachineManager) {
        super(virtualMachineManager);

        // Create communication protocol specific transport.
        this.fTransport = new LocalhostSocketTransport();
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
}
