package org.eclipse.jetty.server;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.component.Graceful;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.Scheduler;

public interface Connector extends LifeCycle, Graceful {// http://blog.csdn.net/tomato__/article/details/32697679
    public Server getServer();
    public Executor getExecutor();
    public Scheduler getScheduler();
    public ByteBufferPool getByteBufferPool();
    public ConnectionFactory getConnectionFactory(String nextProtocol);
    public <T> T getConnectionFactory(Class<T> factoryType);
    public ConnectionFactory getDefaultConnectionFactory();
    public Collection<ConnectionFactory> getConnectionFactories();
    public List<String> getProtocols();

    public long getIdleTimeout();

    public Object getTransport();
    public Collection<EndPoint> getConnectedEndPoints();
    public String getName();
}
