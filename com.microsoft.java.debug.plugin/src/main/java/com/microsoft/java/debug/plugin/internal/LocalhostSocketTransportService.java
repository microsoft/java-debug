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
 *     			from Eclipse
 *     Google Inc - add support for accepting multiple connections
 *******************************************************************************/
package com.microsoft.java.debug.plugin.internal;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import org.eclipse.jdi.TimeoutException;
import org.eclipse.jdi.internal.connect.ConnectMessages;

import com.sun.jdi.connect.TransportTimeoutException;
import com.sun.jdi.connect.spi.ClosedConnectionException;
import com.sun.jdi.connect.spi.Connection;
import com.sun.jdi.connect.spi.TransportService;

public class LocalhostSocketTransportService extends TransportService {
	/** Handshake bytes used just after connecting VM. */
	private static final byte[] handshakeBytes = "JDWP-Handshake".getBytes(); //$NON-NLS-1$

	private Capabilities fCapabilities = new Capabilities() {
		@Override
		public boolean supportsAcceptTimeout() {
			return true;
		}

		@Override
		public boolean supportsAttachTimeout() {
			return true;
		}

		@Override
		public boolean supportsHandshakeTimeout() {
			return true;
		}

		@Override
		public boolean supportsMultipleConnections() {
			return false;
		}
	};

	private static class SocketListenKey extends ListenKey {
		private String fAddress;

		SocketListenKey(String address) {
			fAddress = address;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.sun.jdi.connect.spi.TransportService.ListenKey#address()
		 */
		@Override
		public String address() {
			return fAddress;
		}
	}

	// for listening or accepting connectors
	private ServerSocket fServerSocket;

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.sun.jdi.connect.spi.TransportService#accept(com.sun.jdi.connect.spi
	 * .TransportService.ListenKey, long, long)
	 */
	@Override
	public Connection accept(ListenKey listenKey, long attachTimeout,
			long handshakeTimeout) throws IOException {
		if (attachTimeout > 0) {
			if (attachTimeout > Integer.MAX_VALUE) {
				attachTimeout = Integer.MAX_VALUE; // approx 25 days!
			}
			fServerSocket.setSoTimeout((int) attachTimeout);
		}
		Socket socket;
		try {
			socket = fServerSocket.accept();
		} catch (SocketTimeoutException e) {
			throw new TransportTimeoutException();
		}
		InputStream input = socket.getInputStream();
		OutputStream output = socket.getOutputStream();
		performHandshake(input, output, handshakeTimeout);
		return new LocalhostSocketConnection(socket, input, output);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.sun.jdi.connect.spi.TransportService#attach(java.lang.String,
	 * long, long)
	 */
	@Override
	public Connection attach(String address, long attachTimeout,
			long handshakeTimeout) throws IOException {
		String[] strings = address.split(":"); //$NON-NLS-1$
		String host = "localhost"; //$NON-NLS-1$
		int port = 0;
		if (strings.length == 2) {
			host = strings[0];
			port = Integer.parseInt(strings[1]);
		} else {
			port = Integer.parseInt(strings[0]);
		}

		return attach(host, port, attachTimeout, handshakeTimeout);
	}

	public Connection attach(final String host, final int port,
			long attachTimeout, final long handshakeTimeout) throws IOException {
		if (attachTimeout > 0) {
			if (attachTimeout > Integer.MAX_VALUE) {
				attachTimeout = Integer.MAX_VALUE; // approx 25 days!
			}
		}

		final IOException[] ex = new IOException[1];
		final LocalhostSocketConnection[] result = new LocalhostSocketConnection[1];
		Thread attachThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Socket socket = new Socket(host, port);
					InputStream input = socket.getInputStream();
					OutputStream output = socket.getOutputStream();
					performHandshake(input, output, handshakeTimeout);
					result[0] = new LocalhostSocketConnection(socket, input, output);
				} catch (IOException e) {
					ex[0] = e;
				}
			}
		}, ConnectMessages.SocketTransportService_0);
		attachThread.setDaemon(true);
		attachThread.start();
		try {
			attachThread.join(attachTimeout);
			if (attachThread.isAlive()) {
				attachThread.interrupt();
				throw new TimeoutException();
			}
		} catch (InterruptedException e) {
		}

		if (ex[0] != null) {
			throw ex[0];
		}

		return result[0];
	}

	void performHandshake(final InputStream in, final OutputStream out,
			final long timeout) throws IOException {
		final IOException[] ex = new IOException[1];
		final boolean[] handshakeCompleted = new boolean[1];

		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					writeHandshake(out);
					readHandshake(in);
					handshakeCompleted[0] = true;
				} catch (IOException e) {
					ex[0] = e;
				}
			}
		}, ConnectMessages.SocketTransportService_1);
		t.setDaemon(true);
		t.start();
		try {
			t.join(timeout);
		} catch (InterruptedException e1) {
		}

		if (handshakeCompleted[0]) {
			return;
		}

		try {
			in.close();
			out.close();
		} catch (IOException e) {
		}

		if (ex[0] != null) {
			throw ex[0];
		}

		throw new TransportTimeoutException();
	}

	private void readHandshake(InputStream input) throws IOException {
		try {
			DataInputStream in = new DataInputStream(input);
			byte[] handshakeInput = new byte[handshakeBytes.length];
			in.readFully(handshakeInput);
			if (!Arrays.equals(handshakeInput, handshakeBytes)) {
				throw new IOException("Received invalid handshake"); //$NON-NLS-1$
			}
		} catch (EOFException e) {
			throw new ClosedConnectionException();
		}
	}

	private void writeHandshake(OutputStream out) throws IOException {
		out.write(handshakeBytes);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.sun.jdi.connect.spi.TransportService#capabilities()
	 */
	@Override
	public Capabilities capabilities() {
		return fCapabilities;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.sun.jdi.connect.spi.TransportService#description()
	 */
	@Override
	public String description() {
		return "org.eclipse.jdt.debug: Socket Implementation of TransportService"; //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.sun.jdi.connect.spi.TransportService#name()
	 */
	@Override
	public String name() {
		return "org.eclipse.jdt.debug_SocketTransportService"; //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.sun.jdi.connect.spi.TransportService#startListening()
	 */
	@Override
	public ListenKey startListening() throws IOException {
		// not used by jdt debug.
		return startListening(null);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.sun.jdi.connect.spi.TransportService#startListening(java.lang.String)
	 */
	@Override
	public ListenKey startListening(String address) throws IOException {
		String host = "localhost";
		InetAddress bindAddr = InetAddress.getLocalHost();
		int port = -1;
		if (address != null) {
			// jdt debugger will always specify an address in
			// the form localhost:port
			String[] strings = address.split(":"); //$NON-NLS-1$
			if (strings.length == 2) {
				host = strings[0];
				bindAddr = InetAddress.getByName(host);
				port = Integer.parseInt(strings[1]);
			} else {
				port = Integer.parseInt(strings[0]);
			}
		}
		if (port == -1) {
			throw new IOException("Unable to decode port from address: " + address); //$NON-NLS-1$
		}

		fServerSocket = new ServerSocket(port, 50, bindAddr);
		port = fServerSocket.getLocalPort();
		ListenKey listenKey = new SocketListenKey(host + ":" + port); //$NON-NLS-1$
		return listenKey;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.sun.jdi.connect.spi.TransportService#stopListening(com.sun.jdi.connect
	 * .spi.TransportService.ListenKey)
	 */
	@Override
	public void stopListening(ListenKey arg1) throws IOException {
		if (fServerSocket != null) {
			try {
				fServerSocket.close();
			} catch (IOException e) {
			}
		}
		fServerSocket = null;
	}
}
