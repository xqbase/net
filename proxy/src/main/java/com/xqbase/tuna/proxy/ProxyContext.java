package com.xqbase.tuna.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import javax.net.ssl.SSLContext;

import com.xqbase.tuna.Connection;
import com.xqbase.tuna.Connector;
import com.xqbase.tuna.EventQueue;
import com.xqbase.tuna.ServerConnection;
import com.xqbase.tuna.http.HttpPacket;
import com.xqbase.tuna.ssl.SSLManagers;
import com.xqbase.util.function.ConsumerEx;

public class ProxyContext implements Connector, EventQueue, Executor {
	public static final int FORWARDED_TRANSPARENT = 0;
	public static final int FORWARDED_DELETE = 1;
	public static final int FORWARDED_OFF = 2;
	public static final int FORWARDED_TRUNCATE = 3;
	public static final int FORWARDED_ON = 4;

	public static final int LOG_NONE = 0;
	public static final int LOG_DEBUG = 1;
	public static final int LOG_VERBOSE = 2;

	private static SSLContext defaultSSLContext;

	static {
		try {
			defaultSSLContext = SSLContext.getInstance("TLS");
			defaultSSLContext.init(SSLManagers.DEFAULT_KEY_MANAGERS,
					SSLManagers.DEFAULT_TRUST_MANAGERS, null);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private Connector connector;
	private EventQueue eventQueue;
	private Executor executor;
	private SSLContext sslc = defaultSSLContext;
	private BiPredicate<String, String> auth = (t, u) -> true;
	private UnaryOperator<String> lookup = t -> t;
	private ConsumerEx<HttpPacket, OnRequestException> onRequest = t -> {/**/};
	private BiConsumer<HttpPacket, HttpPacket>
			onResponse = (t, u) -> {/**/}, onComplete = (t, u) -> {/**/};
	private IntFunction<byte[]> errorPages = t -> new byte[0];
	private String realm = null;
	private boolean enableReverse = false;
	private int forwardedType = FORWARDED_TRANSPARENT, logLevel = LOG_NONE;

	public ProxyContext(Connector connector, EventQueue eventQueue, Executor executor) {
		this.connector = connector;
		this.eventQueue = eventQueue;
		this.executor = executor;
	}

	@Override
	public Closeable add(ServerConnection serverConnection,
			InetSocketAddress socketAddress) throws IOException {
		return connector.add(serverConnection, socketAddress);
	}

	@Override
	public void connect(Connection connection,
			InetSocketAddress socketAddress) throws IOException {
		connector.connect(connection, socketAddress);
	}

	@Override
	public void invokeLater(Runnable runnable) {
		eventQueue.invokeLater(runnable);
	}

	@Override
	public void execute(Runnable command) {
		executor.execute(command);
	}

	public void setSSLContext(SSLContext sslc) {
		this.sslc = sslc;
	}

	public void setAuth(BiPredicate<String, String> auth) {
		this.auth = auth;
	}

	public void setLookup(UnaryOperator<String> lookup) {
		this.lookup = lookup;
	}

	public void setOnRequest(ConsumerEx<HttpPacket, OnRequestException> onRequest) {
		this.onRequest = onRequest;
	}

	public void setOnResponse(BiConsumer<HttpPacket, HttpPacket> onResponse) {
		this.onResponse = onResponse;
	}

	public void setOnComplete(BiConsumer<HttpPacket, HttpPacket> onComplete) {
		this.onComplete = onComplete;
	}

	public void setErrorPages(IntFunction<byte[]> errorPages) {
		this.errorPages = errorPages;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public void setEnableReverse(boolean enableReverse) {
		this.enableReverse = enableReverse;
	}

	public void setForwardedType(int forwardedType) {
		this.forwardedType = forwardedType;
	}

	public void setLogLevel(int logLevel) {
		this.logLevel = logLevel;
	}

	public SSLContext getSSLContext() {
		return sslc;
	}

	public boolean auth(String username, String password) {
		return auth.test(username, password);
	}

	public String lookup(String host) {
		return lookup.apply(host);
	}

	public void onRequest(HttpPacket request) throws OnRequestException {
		onRequest.accept(request);
	}

	public void onResponse(HttpPacket request, HttpPacket response) {
		onResponse.accept(request, response);
	}

	public void onComplete(HttpPacket request, HttpPacket response) {
		onComplete.accept(request, response);
	}

	public byte[] getErrorPage(int status) {
		return errorPages.apply(status);
	}

	public String getRealm() {
		return realm;
	}

	public boolean isEnableReverse() {
		return enableReverse;
	}

	public int getForwardedType() {
		return forwardedType;
	}

	public int getLogLevel() {
		return logLevel;
	}
}