package com.xqbase.tuna.mux;

import com.xqbase.tuna.Connection;
import com.xqbase.tuna.Connector;
import com.xqbase.tuna.ServerConnection;
import com.xqbase.tuna.TimerHandler;
import com.xqbase.tuna.packet.PacketFilter;
import com.xqbase.tuna.util.Bytes;

class EdgeMuxConnection extends MuxClientConnection {
	private static final int HEAD_SIZE = MuxPacket.HEAD_SIZE;

	private TimerHandler.Closeable closeable = null;
	private MuxContext context;
	private byte[] authPhrase;

	EdgeMuxConnection(MuxContext context, byte[] authPhrase) {
		super(context, false);
		this.context = context;
		this.authPhrase = authPhrase;
	}

	@Override
	public void onRecv(byte[] b, int off, int len) {
		MuxPacket packet = new MuxPacket(b, off);
		switch (packet.cmd) {
		case MuxPacket.SERVER_PONG:
		case MuxPacket.SERVER_AUTH_OK:
			return;
		case MuxPacket.SERVER_AUTH_ERROR:
		case MuxPacket.SERVER_AUTH_NEED:
			context.test(Bytes.sub(b, off + HEAD_SIZE, packet.size));
			return;
		default:
			onRecv(packet, b, off + HEAD_SIZE);
		}
	}

	@Override
	public void onConnect(String localAddr, int localPort,
			String remoteAddr, int remotePort) {
		super.onConnect(localAddr, localPort, remoteAddr, remotePort);
		if (authPhrase != null) {
			byte[] b = new byte[HEAD_SIZE + authPhrase.length];
			System.arraycopy(authPhrase, 0, b, HEAD_SIZE, authPhrase.length);
			MuxPacket.send(handler, b, MuxPacket.CLIENT_AUTH, 0);
		}
		closeable = context.scheduleDelayed(() -> {
			MuxPacket.send(handler, MuxPacket.CLIENT_PING, 0);
		}, 45000, 45000);
	}

	@Override
	public void onDisconnect() {
		super.onDisconnect();
		if (closeable != null) {
			closeable.close();
		}
	}
}

/**
 * An edge server for the {@link OriginServer}. All the accepted connections
 * will become the virtual connections of the connected OriginServer.<p>
 * The edge connection must be connected to the OriginServer immediately,
 * before the EdgeServer added into a {@link Connector}.<p>
 * For detailed usage, see {@link com.xqbase.tuna.cli.Edge} in github.com
 */
public class EdgeServer implements ServerConnection {
	private EdgeMuxConnection mux;

	/** 
	 * Creates an Edge Server to an {@link OriginServer}<p>
	 * <b>WARNING: be sure to connect with {@link #getMuxConnection()} before listen</b>
	 */
	public EdgeServer(MuxContext context, byte[] authPhrase) {
		mux = new EdgeMuxConnection(context, authPhrase);
	}

	public Connection getMuxConnection() {
		return mux.appendFilter(new PacketFilter(MuxPacket.PARSER));
	}

	public void disconnect() {
		mux.disconnect();
	}

	@Override
	public Connection get() {
		return new TerminalConnection(mux);
	}
}