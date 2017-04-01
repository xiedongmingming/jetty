package org.eclipse.jetty.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.NetworkTrafficListener;
import org.eclipse.jetty.io.NetworkTrafficSelectChannelEndPoint;
import org.eclipse.jetty.io.SelectChannelEndPoint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

public class NetworkTrafficServerConnector extends ServerConnector {// ????

    private final List<NetworkTrafficListener> listeners = new CopyOnWriteArrayList<>();

	// ***********************************************************************************
	public NetworkTrafficServerConnector(Server server) {
        this(server, null, null, null, 0, 0, new HttpConnectionFactory());
    }
	public NetworkTrafficServerConnector(Server server, ConnectionFactory connectionFactory,
			SslContextFactory sslContextFactory) {
        super(server, sslContextFactory, connectionFactory);
    }
	public NetworkTrafficServerConnector(Server server, ConnectionFactory connectionFactory) {
        super(server, connectionFactory);
    }
	public NetworkTrafficServerConnector(Server server, Executor executor, Scheduler scheduler, ByteBufferPool pool,
			int acceptors, int selectors, ConnectionFactory... factories) {
        super(server, executor, scheduler, pool, acceptors, selectors, factories);
    }
	public NetworkTrafficServerConnector(Server server, SslContextFactory sslContextFactory) {
        super(server, sslContextFactory);
    }
	// ***********************************************************************************

	public void addNetworkTrafficListener(NetworkTrafficListener listener) {
        listeners.add(listener);
    }
	public void removeNetworkTrafficListener(NetworkTrafficListener listener) {
        listeners.remove(listener);
    }

	// ***********************************************************************************
    @Override
	protected SelectChannelEndPoint newEndPoint(SocketChannel channel, ManagedSelector selectSet, SelectionKey key)
			throws IOException {// 为新的连接建立一个端点
        NetworkTrafficSelectChannelEndPoint endPoint = new NetworkTrafficSelectChannelEndPoint(channel, selectSet, key, getScheduler(), getIdleTimeout(), listeners);
        return endPoint;
    }
}
