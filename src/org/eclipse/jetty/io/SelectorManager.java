package org.eclipse.jetty.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.Scheduler;

public abstract class SelectorManager extends ContainerLifeCycle implements Dumpable {

	// 所有的选择器都是由管理器管理
    
	public static final int DEFAULT_CONNECT_TIMEOUT = 15000;
    
	protected static final Logger LOG = Log.getLogger(SelectorManager.class);

	private final Executor executor;// SERVERCONNECTOR中的:QueuedThreadPool
	private final Scheduler scheduler;// SERVERCONNECTOR中的:ScheduledExecutorScheduler

	private final ManagedSelector[] _selectors;// 被管理的所有选择器

    private long _connectTimeout = DEFAULT_CONNECT_TIMEOUT;
	private ExecutionStrategy.Factory _executionFactory = ExecutionStrategy.Factory.getDefault();// 表示执行策略(选择器的执行策略)
    private long _selectorIndex;

	//****************************************************************************
    protected SelectorManager(Executor executor, Scheduler scheduler) {
        this(executor, scheduler, (Runtime.getRuntime().availableProcessors() + 1) / 2);
    }
	protected SelectorManager(Executor executor, Scheduler scheduler, int selectors) {//
        if (selectors <= 0) {
			throw new IllegalArgumentException("no selectors");
		}
        this.executor = executor;
        this.scheduler = scheduler;
		_selectors = new ManagedSelector[selectors];// 在启动时创建(这只是个数组而已)
    }
	//****************************************************************************
    public Executor getExecutor() {
        return executor;
    }
    public Scheduler getScheduler() {
        return scheduler;
    }
    public long getConnectTimeout() {
        return _connectTimeout;
    }
    public void setConnectTimeout(long milliseconds) {
        _connectTimeout = milliseconds;
    }
    public ExecutionStrategy.Factory getExecutionStrategyFactory() {
        return _executionFactory;
    }
    public void setExecutionStrategyFactory(ExecutionStrategy.Factory _executionFactory) {
		if (isRunning()) {
			throw new IllegalStateException("cannot change " + ExecutionStrategy.Factory.class.getSimpleName() + " after start()");
		}
        this._executionFactory = _executionFactory;
    }

    @Deprecated
    public int getSelectorPriorityDelta() {
        return 0;
    }
    @Deprecated
    public void setSelectorPriorityDelta(int selectorPriorityDelta) {
    }

	protected void execute(Runnable task) {// 在哪调用--ManagedSelector启动时调用
		executor.execute(task);// 线程池执行任务--ManagedSelector
    }
    public int getSelectorCount() {
        return _selectors.length;
    }

	private ManagedSelector chooseSelector(SocketChannel channel) {// 为指定客户端CHANNEL选择一个选择器

        ManagedSelector candidate1 = null;

        if (channel != null) {

            try {

                SocketAddress remote = channel.getRemoteAddress();

				if (remote instanceof InetSocketAddress) {// 根据对端的IP地址的最后一个字节来选择

					byte[] addr = ((InetSocketAddress) remote).getAddress().getAddress();

                    if (addr != null) {

						int s = addr[addr.length - 1] & 0xFF;

                        candidate1 = _selectors[s % getSelectorCount()];
                    }
                }
            } catch (IOException x) {
                LOG.ignore(x);
            }
        }

        long s = _selectorIndex++;

		int index = (int) (s % getSelectorCount());

        ManagedSelector candidate2 = _selectors[index];

		if (candidate1 == null || candidate1.size() >= candidate2.size() * 2) {
			return candidate2;
		}

        return candidate1;
    }
    public void connect(SocketChannel channel, Object attachment) {

        ManagedSelector set = chooseSelector(channel);

        set.submit(set.new Connect(channel, attachment));
    }
	//***********************************************************************************
	public void accept(SocketChannel channel) {// 处理监听到的所有客户端CHANNEL

        accept(channel, null);
    }

	public void accept(SocketChannel channel, Object attachment) {// 第二个参数为空

		final ManagedSelector selector = chooseSelector(channel);// 为该生成的客户端CHANNEL分配一个选择器

		selector.submit(selector.new Accept(channel, attachment));// 被提交到线程池中执行???特殊语法
    }
	//***********************************************************************************
	public void acceptor(ServerSocketChannel server) {// 当父类打开服务器监听后

        final ManagedSelector selector = chooseSelector(null);

        selector.submit(selector.new Acceptor(server));
    }
    protected void accepted(SocketChannel channel) throws IOException {
        throw new UnsupportedOperationException();
    }
	//***********************************************************************************
    @Override
	protected void doStart() throws Exception {// 启动选择器管理器
        for (int i = 0; i < _selectors.length; i++) {
            ManagedSelector selector = newSelector(i);
            _selectors[i] = selector;
			addBean(selector);// org.eclipse.jetty.server.ServerConnector$ServerConnectorManager
        }
		super.doStart();// 启动刚刚添加的两个选择器
    }
    protected ManagedSelector newSelector(int id) {
        return new ManagedSelector(this, id, getExecutionStrategyFactory());
    }
	//***********************************************************************************
    @Override
    protected void doStop() throws Exception {
        super.doStop();
        for (ManagedSelector selector : _selectors) {
			removeBean(selector);
		}
    }
    protected void endPointOpened(EndPoint endpoint) {
        endpoint.onOpen();
    }
    protected void endPointClosed(EndPoint endpoint) {
        endpoint.onClose();
    }
    public void connectionOpened(Connection connection) {
        try {
            connection.onOpen();
        } catch (Throwable x) {
            if (isRunning()) {
                LOG.warn("Exception while notifying connection " + connection, x);
            } else {
                LOG.debug("Exception while notifying connection " + connection, x);
			}
            throw x;
        }
    }
    public void connectionClosed(Connection connection) {
        try {
            connection.onClose();
        } catch (Throwable x) {
            LOG.debug("Exception while notifying connection " + connection, x);
        }
    }

    protected boolean finishConnect(SocketChannel channel) throws IOException {
        return channel.finishConnect();// finishes the process of connecting a socket channel.
    }
    protected void connectionFailed(SocketChannel channel, Throwable ex, Object attachment) {
        LOG.warn(String.format("%s - %s", channel, attachment), ex);
    }

	// **********************************************************************************************************
	// 底层实现为: ServerConnector.ServerConnectorManager
    protected abstract EndPoint newEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey selectionKey) throws IOException;
    public abstract Connection newConnection(SocketChannel channel, EndPoint endpoint, Object attachment) throws IOException;
	// **********************************************************************************************************
}
