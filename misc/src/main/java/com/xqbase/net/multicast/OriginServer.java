package com.xqbase.net.multicast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import com.xqbase.net.Connection;
import com.xqbase.net.Connection.Event;
import com.xqbase.net.Connection.Writer;
import com.xqbase.net.Connector.Listener;
import com.xqbase.net.FilterFactory;
import com.xqbase.net.ServerConnection;
import com.xqbase.net.packet.PacketFilter;
import com.xqbase.util.Bytes;

class VirtualWriter implements Writer {
	EdgeConnection edge;
	int connId;

	VirtualWriter(EdgeConnection edge, int connId) {
		this.edge = edge;
		this.connId = connId;
	}

	@Override
	public int write(byte[] b, int off, int len) {
		edge.send(new MulticastPacket(connId,
				MulticastPacket.ORIGIN_DATA, 0, len).getHead());
		edge.send(b, off, len);
		return len;
	}
}

class EdgeConnection extends Connection {
	HashMap<Integer, Connection> connMap = new HashMap<>();

	private OriginServer origin;

	EdgeConnection(OriginServer origin) {
		this.origin = origin;
		appendFilter(new PacketFilter(MulticastPacket.getParser()));
	}

	@Override
	protected void onRecv(byte[] b, int off, int len) {
		MulticastPacket packet = new MulticastPacket(b, off, len);
		int connId = packet.connId;
		int command = packet.command;
		if (command == MulticastPacket.EDGE_PING) {
			send(new MulticastPacket(0, MulticastPacket.ORIGIN_PONG, 0, 0).getHead());
		} else if (command == MulticastPacket.EDGE_CONNECT) {
			if (connMap.containsKey(Integer.valueOf(connId))) {
				// throw new PacketException("#" + connId + " Already Exists");
				disconnect();
				return;
			}
			if (len != 32) {
				// throw new PacketException("Wrong Packet Size");
				disconnect();
				return;
			}
			InetAddress addr;
			int port;
			InetSocketAddress local, remote;
			try {
				addr = InetAddress.getByAddress(Bytes.sub(b, off + 16, 4));
				port = Bytes.toShort(b, off + 20) & 0xffff;
				local = new InetSocketAddress(addr, port);
				addr = InetAddress.getByAddress(Bytes.sub(b, off + 24, 4));
				port = Bytes.toShort(b, off + 28) & 0xffff;
				remote = new InetSocketAddress(addr, port);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			Connection conn = origin.createVirtualConnection();
			VirtualWriter writer = new VirtualWriter(this, connId);
			origin.writerMap.put(conn, writer);
			connMap.put(Integer.valueOf(connId), conn);
			conn.setWriter(writer, local, remote, origin.consumer, origin.eventQueue);
			conn.appendFilters(origin.getVirtualFilterFactories());
			conn.getNetReader().onConnect();
			if (conn instanceof Listener) {
				origin.listeners.add((Listener) conn);
			}
		} else {
			Connection conn = connMap.get(Integer.valueOf(connId));
			if (conn == null) {
				return;
			}
			if (command == MulticastPacket.EDGE_DATA) {
				if (16 + packet.size > len) {
					// throw new PacketException("Wrong Packet Size");
					disconnect();
					return;
				}
				conn.getNetReader().onRecv(b, off + 16, packet.size);
			} else {
				packet = new MulticastPacket(connId,
						MulticastPacket.ORIGIN_CLOSE, 0, 0);
				send(packet.getHead());
				conn.dispatchNow(-1);
			}
		}
	}

	@Override
	protected void onDisconnect() {
		for (Connection conn : connMap.values().toArray(new Connection[0])) {
			// "conn.onDisconnect()" might change "connMap"
			conn.dispatchNow(-1);
		}
	}
}

/**
 * An origin server can manage a large number of virtual {@link Connection}s
 * via several {@link EdgeServer}s.
 * @see EdgeServer
 */
public class OriginServer extends ServerConnection implements Listener {
	ConcurrentLinkedQueue<Event> eventQueue = new ConcurrentLinkedQueue<>();
	LinkedHashMap<Connection, VirtualWriter> writerMap = new LinkedHashMap<>();
	LinkedHashSet<Listener> listeners = new LinkedHashSet<>();
	Consumer<Event> consumer = event -> {
		Connection conn = event.getConnection();
		if (!writerMap.containsKey(conn)) {
			return;
		}
		int type = event.getType();
		if (type >= 0) {
			conn.getNetReader().onEvent(event);
		}
		if (type <= 0) {
			VirtualWriter writer = writerMap.remove(conn);
			writer.edge.connMap.remove(Integer.valueOf(writer.connId));
			// Remove the connection before "onDisconnect()",
			// and recursive "disconnect()" can be avoided.
			conn.getNetReader().onDisconnect();
			if (conn instanceof Listener) {
				listeners.remove(conn);
			}
			if (type == 0) {
				writer.edge.send(new MulticastPacket(writer.connId,
						MulticastPacket.ORIGIN_DISCONNECT, 0, 0).getHead());
			}
		}
		return;
	};

	/** Creates an OriginServer with the given port for edge connections. */
	public OriginServer(int port) throws IOException {
		super(port);
	}

	/** @return A virtual {@link Connection} belongs to the origin server. */
	protected Connection createVirtualConnection() {
		return new Connection();
	}

	@Override
	protected Connection createConnection() {
		return new EdgeConnection(this);
	}

	@Override
	public void onEvent() {
		Event event;
		while ((event = eventQueue.poll()) != null) {
			consumer.accept(event);
		}
		for (Listener listener : listeners.toArray(new Listener[0])) {
			// "listener.onEvent()" might change "listeners" 
			listener.onEvent();
		}
	}

	/** @return A set of all virtual connections. */
	public Set<Connection> getVirtualConnectionSet() {
		return writerMap.keySet();
	}

	private ArrayList<FilterFactory> virtualFilterFactories = new ArrayList<>();

	/** @return A list of {@link FilterFactory}s applied to virtual connections. */
	public ArrayList<FilterFactory> getVirtualFilterFactories() {
		return virtualFilterFactories;
	}

	/**
	 * The broadcasting to a large number of virtual connections can be done via a multicast
	 * connection, which can save the network bandwith by the multicast approach.
	 * @param connections - A large number of virtual connections where data is broadcasted.
	 * @return The write-only multicast connection.
	 */
	public Connection createMulticast(Iterable<? extends Connection> connections) {
		Connection multicast = createVirtualConnection();
		multicast.setWriter((b, off, len) -> {
			int maxNumConns = (65535 - 16 - len) / 4;
			if (maxNumConns < 1) {
				throw new IOException("Data Too Long");
			}
			HashMap<EdgeConnection, ArrayList<Integer>> connListMap = new HashMap<>();
			// "connections.iterator()" is called
			for (Connection conn : connections) {
				// nothing can change "connections", so the iteration is safe
				VirtualWriter writer = writerMap.get(conn);
				if (writer == null) {
					continue;
				}
				EdgeConnection edge = writer.edge;
				ArrayList<Integer> connList = connListMap.get(edge);
				if (connList == null) {
					connList = new ArrayList<>();
					connListMap.put(edge, connList);
				}
				connList.add(Integer.valueOf(writer.connId));
			}
			for (Entry<EdgeConnection, ArrayList<Integer>> entry : connListMap.entrySet()) {
				EdgeConnection edge = entry.getKey();
				ArrayList<Integer> connList = entry.getValue();
				int numConnsToSend = connList.size();
				int numConnsSent = 0;
				while (numConnsToSend > 0) {
					int numConns = Math.min(numConnsToSend, maxNumConns);
					edge.send(new MulticastPacket(0,
							MulticastPacket.ORIGIN_MULTICAST, numConns, len).getHead());
					byte[] connListBytes = new byte[numConns * 4];
					for (int i = 0; i < numConns; i ++) {
						Bytes.setInt(connList.get(numConnsSent + i).intValue(),
								connListBytes, i * 4);
					}
					edge.send(connListBytes);
					edge.send(b, off, len);
					numConnsToSend -= numConns;
					numConnsSent += numConns;
				}
			}
			return len;
		});
		multicast.appendFilters(virtualFilterFactories);
		return multicast;
	}
}