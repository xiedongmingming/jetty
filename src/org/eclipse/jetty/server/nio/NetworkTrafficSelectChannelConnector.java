package org.eclipse.jetty.server.nio;

import java.util.concurrent.Executor;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

//deprecated use org.eclipse.jetty.server.NetworkTrafficServerConnector instead.
@Deprecated
public class NetworkTrafficSelectChannelConnector extends NetworkTrafficServerConnector {
    public NetworkTrafficSelectChannelConnector(Server server) {
        super(server);
    }
    public NetworkTrafficSelectChannelConnector(Server server, ConnectionFactory connectionFactory, SslContextFactory sslContextFactory) {
        super(server, connectionFactory, sslContextFactory);
    }
    public NetworkTrafficSelectChannelConnector(Server server, ConnectionFactory connectionFactory) {
        super(server, connectionFactory);
    }
    public NetworkTrafficSelectChannelConnector(Server server, Executor executor, Scheduler scheduler, ByteBufferPool pool, int acceptors, int selectors, ConnectionFactory... factories) {
        super(server, executor, scheduler, pool, acceptors, selectors, factories);
    }
    public NetworkTrafficSelectChannelConnector(Server server, SslContextFactory sslContextFactory) {
        super(server, sslContextFactory);
    }
}