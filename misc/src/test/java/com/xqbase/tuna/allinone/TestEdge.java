package com.xqbase.tuna.allinone;

import java.io.IOException;

import com.xqbase.tuna.ConnectionWrapper;
import com.xqbase.tuna.ConnectorImpl;
import com.xqbase.tuna.allinone.EdgeServer;
import com.xqbase.tuna.misc.DumpFilter;

public class TestEdge {
	public static void main(String[] args) throws IOException {
		try (ConnectorImpl connector = new ConnectorImpl()) {
			EdgeServer edge = new EdgeServer(connector);
			connector.add(edge, 2626);
			connector.connect(edge.getOriginConnection().
					appendFilter(new DumpFilter()).appendFilter(new ConnectionWrapper() {
				@Override
				public void onDisconnect() {
					super.onDisconnect();
					connector.interrupt();
				}
			}), "127.0.0.1", 2323);
			connector.doEvents();
		}
	}
}