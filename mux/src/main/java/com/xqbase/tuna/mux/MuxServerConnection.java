package com.xqbase.tuna.mux;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;

import com.xqbase.tuna.Connection;
import com.xqbase.tuna.ConnectionHandler;
import com.xqbase.tuna.Connector;
import com.xqbase.tuna.ServerConnection;
import com.xqbase.tuna.util.Bytes;
import com.xqbase.util.Log;

class MuxServerConnection implements Connection {
	private static final int LOG_DEBUG = MuxContext.LOG_DEBUG;
	private static final Connection[] EMPTY_CONNECTIONS = new Connection[0];

	private boolean established = false, guest;
	private boolean[] queued = {false};
	private String recv = "", send = "";
	private ServerConnection server;
	private MuxContext context;
	private int logLevel;

	boolean activeClose = false;
	HashMap<Integer, Connection> connectionMap = new HashMap<>();
	ConnectionHandler handler;

	MuxServerConnection(ServerConnection server, MuxContext context, boolean guest) {
		this.server = server;
		this.context = context;
		this.guest = guest;
		logLevel = context.getLogLevel();
	}

	@Override
	public void setHandler(ConnectionHandler handler) {
		this.handler = handler;
	}

	void onRecv(MuxPacket packet, byte[] b, int off) {
		int cid = packet.cid;
		switch (packet.cmd) {
		case MuxPacket.CONNECTION_RECV:
			Connection connection = connectionMap.get(Integer.valueOf(cid));
			if (connection != null && packet.size > 0) {
				connection.onRecv(b, off, packet.size);
			}
			return;
		case MuxPacket.CONNECTION_CONNECT:
			if (connectionMap.containsKey(Integer.valueOf(cid))) {
				disconnect();
				return;
			}
			connection = server.get();
			String localAddr, remoteAddr;
			int localPort, remotePort;
			if (packet.size == 12 || packet.size == 36) {
				int addrLen = packet.size / 2 - 2;
				try {
					localAddr = InetAddress.getByAddress(Bytes.
							sub(b, off, addrLen)).getHostAddress();
					remoteAddr = InetAddress.getByAddress(Bytes.
							sub(b, off + addrLen + 2, addrLen)).getHostAddress();
				} catch (IOException e) {
					localAddr = remoteAddr = Connector.ANY_LOCAL_ADDRESS;
				}
				localPort = Bytes.toShort(Bytes.
						sub(b, off + addrLen, 2)) & 0xFFFF;
				remotePort = Bytes.toShort(Bytes.
						sub(b, off + addrLen * 2 + 2, 2)) & 0xFFFF;
			} else {
				localAddr = remoteAddr = Connector.ANY_LOCAL_ADDRESS;
				localPort = remotePort = 0;
			}
			VirtualHandler virtualHandler = new VirtualHandler(this, cid);
			connection.setHandler(virtualHandler);
			connectionMap.put(Integer.valueOf(cid), connection);
			connection.onConnect(localAddr, localPort, remoteAddr, remotePort);
			return;
		case MuxPacket.CONNECTION_QUEUE:
			connection = connectionMap.get(Integer.valueOf(cid));
			if (connection != null && packet.size >= 4) {
				connection.onQueue(Bytes.toInt(b, off));
			}
			return;
		case MuxPacket.CONNECTION_DISCONNECT:
			connection = connectionMap.remove(Integer.valueOf(cid));
			if (connection != null) {
				MuxPacket.send(handler, MuxPacket.HANDLER_CLOSE, cid);
				connection.onDisconnect();
			}
			return;
		}
	}

	@Override
	public void onQueue(int size) {
		if (!context.isQueueChanged(size, queued)) {
			return;
		}
		// Tell all virtual connections that mux is congested or smooth 
		for (Connection connection : connectionMap.
				values().toArray(EMPTY_CONNECTIONS)) {
			// "connecton.onQueue()" might change "connectionMap"
			connection.onQueue(size);
		}
		if (logLevel >= LOG_DEBUG) {
			Log.d((size == 0 ?
				"All Virtual Connections Unblocked due to Smooth Mux" :
				"All Virtual Connections Blocked due to Congested Mux (" +
				size + ")") + send);
		}
	}

	@Override
	public void onConnect(String localAddr, int localPort,
			String remoteAddr, int remotePort) {
		established = true;
		if (logLevel < LOG_DEBUG) {
			return;
		}
		String local = localAddr + ":" + localPort;
		String remote = remoteAddr + ":" + remotePort;
		if (guest) {
			recv = ", " + local + " <= " + remote;
			send = ", " + local + " => " + remote;
		} else {
			recv = ", " + remote + " => " + local;
			send = ", " + remote + " <= " + local;
		}
		Log.d("Mux Connection Established" + recv);
	}

	@Override
	public void onDisconnect() {
		for (Connection connection : connectionMap.
				values().toArray(EMPTY_CONNECTIONS)) {
			// "connecton.onDisconnect()" might change "connectionMap"
			connection.onDisconnect();
		}
		if (logLevel >= LOG_DEBUG) {
			Log.d(!established ? "Mux Connection Failed" :
					activeClose ? "Mux Connection Disconnected" + send :
					"Mux Connection Lost" + recv);
		}
	}

	void disconnect() {
		activeClose = true;
		handler.disconnect();
		onDisconnect();
	}
}