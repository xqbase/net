package com.xqbase.tuna.allinone;

import java.io.File;
import java.util.LinkedHashSet;

import com.xqbase.tuna.Connection;
import com.xqbase.tuna.ConnectionHandler;
import com.xqbase.tuna.ConnectorImpl;
import com.xqbase.tuna.ServerConnection;
import com.xqbase.tuna.misc.CrossDomainServer;
import com.xqbase.tuna.misc.DumpFilter;
import com.xqbase.tuna.misc.ZLibFilter;

class BroadcastConnection implements Connection {
	private LinkedHashSet<ConnectionHandler> handlers;
	private ConnectionHandler[] multicast;
	private ConnectionHandler handler;

	public BroadcastConnection(LinkedHashSet<ConnectionHandler> handlers,
			ConnectionHandler[] multicast) {
		this.handlers = handlers;
		this.multicast = multicast;
	}

	@Override
	public void setHandler(ConnectionHandler handler) {
		this.handler = handler;
	}

	@Override
	public void onRecv(byte[] b, int off, int len) {
		multicast[0].send(b, off, len);
	}

	@Override
	public void onConnect() {
		if (MulticastHandler.isMulticast(handler)) {
			multicast[0] = handler;
		} else {
			handlers.add(handler);
		}
	}

	@Override
	public void onDisconnect() {
		handlers.remove(handler);
	}
}

public class TestMulticast {
	public static void main(String[] args) throws Exception {
		LinkedHashSet<ConnectionHandler> handlers = new LinkedHashSet<>();
		ConnectionHandler[] multicast = new ConnectionHandler[1];
		ServerConnection server = ((ServerConnection) () ->
				new BroadcastConnection(handlers, multicast)).
				appendFilter(() -> new DumpFilter().setDumpText(true)).
				appendFilter(ZLibFilter::new);
		Connection connection = server.get();
		connection.setHandler(new MulticastHandler(handlers));
		connection.onConnect();
		try (
			ConnectorImpl connector = new ConnectorImpl();
			OriginServer origin = new OriginServer(server, connector);
		) {
			connector.add(new CrossDomainServer(new File(TestMulticast.class.
					getResource("/crossdomain.xml").toURI())), 843);
			connector.add(origin, 2323);
			EdgeServer edge = new EdgeServer(connector);
			connector.add(edge, 2424);
			connector.connect(edge.getOriginConnection(), "127.0.0.1", 2323);
			edge = new EdgeServer(connector);
			connector.add(edge, 2525);
			connector.connect(edge.getOriginConnection(), "127.0.0.1", 2323);
			connector.doEvents();
		}
	}
}