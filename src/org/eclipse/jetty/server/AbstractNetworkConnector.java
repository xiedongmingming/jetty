package org.eclipse.jetty.server;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.thread.Scheduler;

//所有连接的父类
public abstract class AbstractNetworkConnector extends AbstractConnector implements NetworkConnector {

	private volatile String _host;// 该连接器所在的主机
	private volatile int _port = 0;// 该连接器监听的端口

	// 2.0.1.0 -- 该构造器回去调用父类的构造器
	public AbstractNetworkConnector(Server server, 
			Executor executor, 
			Scheduler scheduler, 
			ByteBufferPool pool, 
			int acceptors, 
			ConnectionFactory... factories) {
        super(server, executor, scheduler, pool, acceptors, factories);
    }
    public void setHost(String host) {
        _host = host;
    }

	public void setPort(int port) {
		_port = port;
	}
    @Override
    public String getHost() {
        return _host;
    }
    @Override
    public int getPort() {
        return _port;
    }
	@Override
	public int getLocalPort() {
        return -1;
    }

	@Override
	protected void doStart() throws Exception {// 4.0.0.2 -- 启动过程
		System.out.println("打开网络连接器的网络监听");
		open();// 调用底层实现--打开网络监听
		super.doStart();// AbstractConnector
    }

	@Override
	protected void doStop() throws Exception {
        close();
        super.doStop();
    }

	@Override
	public void open() throws IOException {

    }

	@Override
	public void close() {
        interruptAcceptors();//interrupting is often sufficient to close the channel
    }

	@Override
	public Future<Void> shutdown() {
        close();
        return super.shutdown();
    }

	@Override
	protected boolean isAccepting() {
        return super.isAccepting() && isOpen();
    }

	@Override
	public String toString() {
        return String.format("%s{%s:%d}", super.toString(), getHost() == null ? "0.0.0.0" : getHost(), getLocalPort() <= 0 ? getPort() : getLocalPort());
    }
}
