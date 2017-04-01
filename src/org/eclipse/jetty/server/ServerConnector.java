package org.eclipse.jetty.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.SelectChannelEndPoint;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.Scheduler;

//重要的成员都在抽象连接器对象中
public class ServerConnector extends AbstractNetworkConnector {// JETTY服务使用的默认连接器
	
	// 管理选择器:共有7个BEAN

	private final SelectorManager _manager;// 初始化时新建--管理监听到的网络连接(SOCKET)

	private volatile ServerSocketChannel _acceptChannel;// 底层实现(服务器的监听通道)(作为BEAN)--ServerSocketChannelImpl

	private volatile int _localPort = -1;// 服务端监听的地址


	//***************************************************************************
	public ServerConnector(@Name("server") Server server) {// 默认的连接工厂--HttpConnectionFactory
		this(server, null, null, null, -1, -1, new HttpConnectionFactory());
    }
    public ServerConnector(@Name("server") Server server, @Name("acceptors") int acceptors, @Name("selectors") int selectors) {
        this(server, null, null, null, acceptors, selectors, new HttpConnectionFactory());
    }
    public ServerConnector(@Name("server") Server server, @Name("acceptors") int acceptors, @Name("selectors") int selectors, @Name("factories") ConnectionFactory... factories) {
        this(server, null, null, null, acceptors, selectors, factories);
    }
    public ServerConnector(@Name("server") Server server, @Name("factories") ConnectionFactory... factories) {
		this(server, null, null, null, -1, -1, factories);
    }
    public ServerConnector(@Name("server") Server server, @Name("sslContextFactory") SslContextFactory sslContextFactory) {
		this(server, null, null, null, -1, -1, AbstractConnectionFactory.getFactories(sslContextFactory, new HttpConnectionFactory()));
    }
    public ServerConnector(@Name("server") Server server, @Name("acceptors") int acceptors, @Name("selectors") int selectors, @Name("sslContextFactory") SslContextFactory sslContextFactory) {
        this(server, null, null, null, acceptors, selectors, AbstractConnectionFactory.getFactories(sslContextFactory, new HttpConnectionFactory()));
    }
    public ServerConnector(@Name("server") Server server, @Name("sslContextFactory") SslContextFactory sslContextFactory, @Name("factories") ConnectionFactory... factories) {
		this(server, null, null, null, -1, -1, AbstractConnectionFactory.getFactories(sslContextFactory, factories));
    }
	public ServerConnector( // 2.0.0.0--该构造器会去调用父类的构造器
			@Name("server") Server server, // 服务器实例
			// ************************************************************
			@Name("executor") Executor executor, // 默认为空
			@Name("scheduler") Scheduler scheduler, // 默认为空
			@Name("bufferPool") ByteBufferPool bufferPool, // 默认为空
			// ************************************************************
			@Name("acceptors") int acceptors, // 默认为-1
			@Name("selectors") int selectors, // 默认为-1
			// ************************************************************
			@Name("factories") ConnectionFactory... factories) {// 底层实现--HttpConnectionFactory、AbstractConnectionFactory
        
		super(server, executor, scheduler, bufferPool, acceptors, factories);
        
		// 选择器数量--ServerConnectorManager
		_manager = newSelectorManager(getExecutor(), getScheduler(), selectors > 0 ? selectors : Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));//1-4
        
		addBean(_manager, true);// 添加BEAN
        
		setAcceptorPriorityDelta(-2);
    }

	//***************************************************************************
    protected SelectorManager newSelectorManager(Executor executor, Scheduler scheduler, int selectors) {
        return new ServerConnectorManager(executor, scheduler, selectors);
    }
    @Override
	protected void doStart() throws Exception {// 4.0.0.1--启动过程(CONNECTOR启动最底层实现)--由服务SERVER负责启动

		super.doStart();// AbstractNetworkConnector

		// 在父类中已经打来了网络监听
		if (getAcceptors() == 0) {

            _acceptChannel.configureBlocking(false);

            _manager.acceptor(_acceptChannel);
        }
    }
    @Override
    public boolean isOpen() {
        ServerSocketChannel channel = _acceptChannel;
        return channel != null && channel.isOpen();
    }
    @Deprecated
    public int getSelectorPriorityDelta() {
        return _manager.getSelectorPriorityDelta();
    }
    @Deprecated
    public void setSelectorPriorityDelta(int selectorPriorityDelta) {
        _manager.setSelectorPriorityDelta(selectorPriorityDelta);
    }

	// ******************************************************************************************
	private volatile boolean _inheritChannel = false;// 是否继承CHANNEL????
    public boolean isInheritChannel() {
        return _inheritChannel;
    }
    public void setInheritChannel(boolean inheritChannel) {
        _inheritChannel = inheritChannel;
    }
	// ******************************************************************************************
    @Override
	public void open() throws IOException {// 打开监听SOCKET

		System.out.println("打开服务端的监听SOCKET");

        if (_acceptChannel == null) {

			ServerSocketChannel serverChannel = null;// NIO中提供的类(服务端的SOCKET通道)

			if (isInheritChannel()) {// ???是否通过继承JVM通道来获得CHANNEL

                Channel channel = System.inheritedChannel();

                if (channel instanceof ServerSocketChannel) {
					serverChannel = (ServerSocketChannel) channel;
                } else {
					LOG.warn("unable to use system.inheritedchannel() [{}]. trying a new serversocketchannel at {}:{}", channel, getHost(), getPort());
				}
			}

			if (serverChannel == null) {

				serverChannel = ServerSocketChannel.open();// 具体实现类:ServerSocketChannelImpl

                InetSocketAddress bindAddress = getHost() == null ? new InetSocketAddress(getPort()) : new InetSocketAddress(getHost(), getPort());

				serverChannel.socket().setReuseAddress(getReuseAddress());// 设置是否重用地址
				serverChannel.socket().bind(bindAddress, getAcceptQueueSize());// 绑定地址(第二个参数表示最大等待队列--0表示采用默认值)

				_localPort = serverChannel.socket().getLocalPort();

                if (_localPort <= 0) {
					throw new IOException("server channel not bound");
				}

                addBean(serverChannel);

            }

            serverChannel.configureBlocking(true);

			// 第七个BEAN
			addBean(serverChannel);// 如果已经加过则不会实际再次加入

            _acceptChannel = serverChannel;
        }
    }
    @Override
    public Future<Void> shutdown() {//shutdown all the connections
        return super.shutdown();
    }
    @Override
    public void close() {

        ServerSocketChannel serverChannel = _acceptChannel;

        _acceptChannel = null;

        if (serverChannel != null) {

            removeBean(serverChannel);

			if (serverChannel.isOpen()) {
                try {
                    serverChannel.close();
                } catch (IOException e) {
                    LOG.warn(e);
                }
            }
        }
        _localPort = -2;
    }
	//*******************************************************************************
    @Override
	public void accept(int acceptorID) throws IOException {// 接收底层连接--在服务器启动时会调用

		System.out.println("服务端开始监听");

        ServerSocketChannel serverChannel = _acceptChannel;

        if (serverChannel != null && serverChannel.isOpen()) {

			SocketChannel channel = serverChannel.accept();// 生成客户端的CHANNEL--这里是阻塞监听(SocketChannelImpl)

            accepted(channel);
        }
    }
	private void accepted(SocketChannel channel) throws IOException {// 参数为接收到请求后生成的对应客户端CHANNEL

		channel.configureBlocking(false);// 设置生成的客户端CHANNEL为非阻塞模式

		Socket socket = channel.socket();// 获取对应的SOCKET

		configure(socket);

		_manager.accept(channel);// SelectorManager.accept()
    }
	protected void configure(Socket socket) {// 配置生成的客户端SOCKET

        try {

			socket.setTcpNoDelay(true);

            if (_lingerTime >= 0) {
                socket.setSoLinger(true, _lingerTime / 1000);
            } else {
				socket.setSoLinger(false, 0);
			}

        } catch (SocketException e) {
            LOG.ignore(e);
        }
    }
	//*******************************************************************************
    public SelectorManager getSelectorManager() {
        return _manager;
    }

    @Override
    public Object getTransport() {
        return _acceptChannel;
    }
    @Override
    public int getLocalPort() {
        return _localPort;
    }
	// **************************************************************************
	private volatile int _lingerTime = -1;
    public int getSoLingerTime() {
        return _lingerTime;
    }
    public void setSoLingerTime(int lingerTime) {
        _lingerTime = lingerTime;
    }
	// **************************************************************************
	private volatile int _acceptQueueSize = 0;// 表示服务CHANNEL的最大接收数量
    public int getAcceptQueueSize() {
        return _acceptQueueSize;
    }
    public void setAcceptQueueSize(int acceptQueueSize) {
        _acceptQueueSize = acceptQueueSize;
    }
	// **************************************************************************
	private volatile boolean _reuseAddress = true;// 表示是否重用地址
    public boolean getReuseAddress() {
        return _reuseAddress;
    }
	public void setReuseAddress(boolean reuseAddress) {// 是否重用地址
        _reuseAddress = reuseAddress;
    }
	// **************************************************************************
    public ExecutionStrategy.Factory getExecutionStrategyFactory() {
        return _manager.getExecutionStrategyFactory();
    }
    public void setExecutionStrategyFactory(ExecutionStrategy.Factory executionFactory) {
        _manager.setExecutionStrategyFactory(executionFactory);
    }
	//**************************************************************************
	protected SelectChannelEndPoint newEndPoint(SocketChannel channel, ManagedSelector selectSet, SelectionKey key) throws IOException {

		// 三个参数分别为:
		// 1、监听到的连接
		// 2、所在的选择器(表示该连接被注册到该选择器上了)
		// 3、注册到选择器上时返回的KEY

		return new SelectChannelEndPoint(channel, selectSet, key, getScheduler(), getIdleTimeout());
    }

	protected class ServerConnectorManager extends SelectorManager {// 管理器的最底层实现

        public ServerConnectorManager(Executor executor, Scheduler scheduler, int selectors) {
            super(executor, scheduler, selectors);
        }
        @Override
        protected void accepted(SocketChannel channel) throws IOException {
            ServerConnector.this.accepted(channel);
        }
		@Override
        protected void endPointOpened(EndPoint endpoint) {
			super.endPointOpened(endpoint);// 主要用于开启超时定时器
			onEndPointOpened(endpoint);// 添加到连接器中(集合)
        }
        @Override
        protected void endPointClosed(EndPoint endpoint) {
            onEndPointClosed(endpoint);
            super.endPointClosed(endpoint);
        }
		//**************************************************************************
		// 父类的抽象方法
        @Override
        protected SelectChannelEndPoint newEndPoint(SocketChannel channel, ManagedSelector selectSet, SelectionKey selectionKey) throws IOException {

			// 第一个参数为监听到的通道
			// 第二个参数为为该通道选择的选择器
			// 第三个参数为该通道注册到选择器中时返回的KEY

			return ServerConnector.this.newEndPoint(channel, selectSet, selectionKey);
        }
        @Override
        public Connection newConnection(SocketChannel channel, EndPoint endpoint, Object attachment) throws IOException {
			return getDefaultConnectionFactory().newConnection(ServerConnector.this, endpoint);// HttpConnectionFactory
        }
		//**************************************************************************
    }
}
