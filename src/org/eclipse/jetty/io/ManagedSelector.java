package org.eclipse.jetty.io;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.Locker;
import org.eclipse.jetty.util.thread.Scheduler;

public class ManagedSelector extends AbstractLifeCycle implements Runnable, Dumpable {

	// 本身作为一个任务:
	// 在启动时使用所在管理器的执行器执行自身
	// 用于包装选择器(NIO中的选择器)

	private static final Logger LOG = Log.getLogger(ManagedSelector.class);

	private final Locker _locker = new Locker();// 控制提交的
	
	private boolean _selecting = false;// 表示是否需要继续选择(一次选择完成后需要进行处理当处理完成后需要继续选择)

	private final Queue<Runnable> _actions = new ArrayDeque<>();// 所有待处理的行为

	private final SelectorManager _selectorManager;// 所在的管理器--org.eclipse.jetty.server.ServerConnector$ServerConnectorManager

	private final int _id;// 编号

	private final ExecutionStrategy _strategy;// 表示选择器执行策略:ExecuteProduceConsume

	private Selector _selector;// NIO中的选择器
	
	//***************************************************************************
	// 构造函数
    public ManagedSelector(SelectorManager selectorManager, int id) {
        this(selectorManager, id, ExecutionStrategy.Factory.getDefault());
    }
    public ManagedSelector(SelectorManager selectorManager, int id, ExecutionStrategy.Factory executionFactory) {
		_selectorManager = selectorManager;// 底层实现为:org.eclipse.jetty.server.ServerConnector$ServerConnectorManager
		_id = id;// 选择器编号

		// System.out.println("这里的工厂是: " + executionFactory.getClass().getName());
		// org.eclipse.jetty.util.thread.ExecutionStrategy$DefaultExecutionStrategyFactory

		// ExecuteProduceConsume
		// 使用选择器管理器的执行器
		// 生产器使用: SelectorProducer
		_strategy = executionFactory.newExecutionStrategy(new SelectorProducer(), selectorManager.getExecutor());

		setStopTimeout(5000);
    }
	//***************************************************************************
    public ExecutionStrategy getExecutionStrategy() {
        return _strategy;
    }
	//***************************************************************************
	//
    @Override
    protected void doStart() throws Exception {

        super.doStart();

        _selector = newSelector();

		_selectorManager.execute(this);// org.eclipse.jetty.server.ServerConnector$ServerConnectorManager
    }
    @Override
    protected void doStop() throws Exception {

        CloseEndPoints close_endps = new CloseEndPoints();
        submit(close_endps);
        close_endps.await(getStopTimeout());
        super.doStop();
        CloseSelector close_selector = new CloseSelector();
        submit(close_selector);
        close_selector.await(getStopTimeout());

    }
	//***************************************************************************
	//
	@Override
	public void run() {// 作为任务运行:所在类启动时运行
		_strategy.execute();// 执行策略:ExecuteProduceConsume
    }
	
	@Override
    public String dump() {
        return ContainerLifeCycle.dump(this);
    }
    @Override
    public void dump(Appendable out, String indent) throws IOException {

        out.append(String.valueOf(this)).append(" id=").append(String.valueOf(_id)).append(System.lineSeparator());

        Selector selector = _selector;

        if (selector != null && selector.isOpen()) {

            final ArrayList<Object> dump = new ArrayList<>(selector.keys().size() * 2);

            DumpKeys dumpKeys = new DumpKeys(dump);

            submit(dumpKeys);

            dumpKeys.await(5, TimeUnit.SECONDS);

            ContainerLifeCycle.dump(out, indent, dump);
        }
    }
	//***************************************************************************
    protected Selector newSelector() throws IOException {
		return Selector.open();// NIO中的返回
    }
    public int size() {
        Selector s = _selector;
        if (s == null) {
            return 0;
		}
        return s.keys().size();
    }

	public void submit(Runnable change) {// 用于提交获取到的连接--Accept、CreateEndPoint

        Selector selector = null;

        try (Locker.Lock lock = _locker.lock()) {

			_actions.offer(change);// 将参数插入到队列尾部

			if (_selecting) {// 表示正在选择则停止选择
                selector = _selector;
				_selecting = false;
            }
        }

		if (selector != null) {
			selector.wakeup();
		}
    }

	public interface SelectableEndPoint extends EndPoint {
		Runnable onSelected();
        void updateKey();
    }

	// ********************************************************************************************
	// 在本类的构造器函数创建执行策略时调用
	// 处理接收器接收到的新连接
	// 处理连接上的选定事件
	private class SelectorProducer implements ExecutionStrategy.Producer {// 执行KEY键的选择--负责生产

		private Set<SelectionKey> _keys = Collections.emptySet();
		private Iterator<SelectionKey> _cursor = Collections.emptyIterator();

		@Override
		public Runnable produce() {
			while (true) {
				Runnable task = processSelected();// SelectChannelEndPoint
				if (task != null) {
					return task;
				}
				Runnable action = runActions();
				if (action != null) {
					return action;// PRODUCT -- EndPointCloser
				}
				update();
				if (!select()) {
					return null;
				}
			}
		}

		private Runnable processSelected() {// 处理选中的KEY
			while (_cursor.hasNext()) {
				SelectionKey key = _cursor.next();
				if (key.isValid()) {
					Object attachment = key.attachment();
					try {
						if (attachment instanceof SelectableEndPoint) {
							Runnable task = ((SelectableEndPoint) attachment).onSelected();// try to produce a task
							if (task != null) {
								return task;
							}
						} else if (key.isConnectable()) {
							Runnable task = processConnect(key, (Connect) attachment);
							if (task != null) {
								return task;
							}
						} else if (key.isAcceptable()) {//
							processAccept(key);
						} else {
							throw new IllegalStateException("key=" + key + ", att=" + attachment + ", iOps=" + key.interestOps() + ", rOps=" + key.readyOps());
						}
					} catch (CancelledKeyException x) {
						if (attachment instanceof EndPoint) {
							closeNoExceptions((EndPoint) attachment);
						}
					} catch (Throwable x) {
						if (attachment instanceof EndPoint) {
							closeNoExceptions((EndPoint) attachment);
						}
					}
				} else {

					Object attachment = key.attachment();
					if (attachment instanceof EndPoint) {
						closeNoExceptions((EndPoint) attachment);
					}
				}
			}
			return null;
		}

		private Runnable runActions() {// 挑选一个任务执行
			while (true) {
				Runnable action;
				try (Locker.Lock lock = _locker.lock()) {
					action = _actions.poll();
					if (action == null) {
						_selecting = true;// no more actions, so we need to select
						return null;
					}
				}
				if (action instanceof Product) {
					return action;
				}
				runChange(action);// running the change may queue another action.
			}
		}

		private void runChange(Runnable change) {
			try {
				change.run();
			} catch (Throwable x) {
				LOG.debug("could not run change " + change, x);
			}
		}

		private boolean select() {// 表示进行选择
			try {
				Selector selector = _selector;// WindowsSelectorImpl
				if (selector != null && selector.isOpen()) {
					int selected = selector.select();//
					System.out.println("当前选中的KEY为: " + selected + " --->" + selector.selectedKeys());
					try (Locker.Lock lock = _locker.lock()) {
						_selecting = false;//
					}
					_keys = selector.selectedKeys();
					_cursor = _keys.iterator();
					return true;
				}
			} catch (Throwable x) {
				closeNoExceptions(_selector);
				if (isRunning()) {
					LOG.warn(x);
				} else {
					LOG.debug(x);
				}
			}
			return false;
		}

		private void update() {
			for (SelectionKey key : _keys) {
				updateKey(key);
			}
			_keys.clear();//
		}

		private void updateKey(SelectionKey key) {
			Object attachment = key.attachment();
			if (attachment instanceof SelectableEndPoint) {
				((SelectableEndPoint) attachment).updateKey();//
			}
		}
	}

	private Runnable processConnect(SelectionKey key, final Connect connect) {//
        SocketChannel channel = (SocketChannel)key.channel();
        try {
			key.attach(connect.attachment);//
			boolean connected = _selectorManager.finishConnect(channel);//

			if (connected) {//
				if (connect.timeout.cancel()) {//
                    key.interestOps(0);
					return new CreateEndPoint(channel, key) {//
                        @Override
                        protected void failed(Throwable failure) {
                            super.failed(failure);
                            connect.failed(failure);
                        }
                    };
                } else {
                    throw new SocketTimeoutException("Concurrent Connect Timeout");
                }
            } else {
                throw new ConnectException();
            }
        } catch (Throwable x) {
            connect.failed(x);
            return null;
        }
    }

	private void processAccept(SelectionKey key) {//
        ServerSocketChannel server = (ServerSocketChannel)key.channel();
        SocketChannel channel = null;
        try {
			while ((channel = server.accept()) != null) {// -- ???
                _selectorManager.accepted(channel);
            }
        } catch (Throwable x) {
            closeNoExceptions(channel);
            LOG.warn("Accept failed for channel " + channel, x);
        }
    }
	//***************************************************************************
	private interface Product extends Runnable {

    }
	private void closeNoExceptions(Closeable closeable) {// 关闭连接--SocketChannel
        try {
            if (closeable != null) {
                closeable.close();
			}
        } catch (Throwable x) {
			LOG.ignore(x);
        }
    }
	//***************************************************************************
	private EndPoint createEndPoint(SocketChannel channel, SelectionKey selectionKey) throws IOException {

		// 新建端点: SelectChannelEndPoint
		EndPoint endPoint = _selectorManager.newEndPoint(channel, this, selectionKey);// ServerConnector.ServerConnectorManager

		_selectorManager.endPointOpened(endPoint);// 会回调相关函数

		Connection connection = _selectorManager.newConnection(channel, endPoint, selectionKey.attachment());

		endPoint.setConnection(connection);

		selectionKey.attach(endPoint);// ????

		_selectorManager.connectionOpened(connection);// 表示打开连接--在这里会将网络注册为读事件

		return endPoint;
    }
	public void destroyEndPoint(final EndPoint endPoint) {// 异步销毁一个端点(及其对应的连接)
        final Connection connection = endPoint.getConnection();
        submit(new Product() {
            @Override
            public void run() {
                if (connection != null) {
                    _selectorManager.connectionClosed(connection);
				}
                _selectorManager.endPointClosed(endPoint);
            }
        });
    }
	//***************************************************************************
    @Override
    public String toString() {
        Selector selector = _selector;
        return String.format("%s id=%s keys=%d selected=%d",
                super.toString(),
                _id,
                selector != null && selector.isOpen() ? selector.keys().size() : -1,
                selector != null && selector.isOpen() ? selector.selectedKeys().size() : -1);
    }

    private class DumpKeys implements Runnable {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final List<Object> _dumps;

        private DumpKeys(List<Object> dumps) {
            this._dumps = dumps;
        }

        @Override
        public void run() {
            Selector selector = _selector;
            if (selector != null && selector.isOpen()) {
                Set<SelectionKey> keys = selector.keys();
                _dumps.add(selector + " keys=" + keys.size());
                for (SelectionKey key : keys) {
                    try {
                        _dumps.add(String.format("SelectionKey@%x{i=%d}->%s", key.hashCode(), key.interestOps(), key.attachment()));
                    } catch (Throwable x) {
                        LOG.ignore(x);
                    }
                }
            }
            latch.countDown();
        }

        public boolean await(long timeout, TimeUnit unit) {
            try {
                return latch.await(timeout, unit);
            } catch (InterruptedException x) {
                return false;
            }
        }
    }

    class Acceptor implements Runnable {

        private final ServerSocketChannel _channel;

        public Acceptor(ServerSocketChannel channel) {
            this._channel = channel;
        }

		@SuppressWarnings("unused")
		@Override
        public void run() {
            try {
				SelectionKey key = _channel.register(_selector, SelectionKey.OP_ACCEPT, null);
            } catch (Throwable x) {
                closeNoExceptions(_channel);
                LOG.warn(x);
            }
        }
    }

	// *************************************************************************************************************
	class Accept implements Runnable, Closeable {// 对应接收器接收到的一个连接请求

		private final SocketChannel channel;
        private final Object attachment;

		Accept(SocketChannel channel, Object attachment) {// 封装生成的客户端CHANNEL(第二个参数为空)--便于选择器处理
			this.channel = channel;// 表示对应的连接
            this.attachment = attachment;
        }

		// *****************************************************************************
        @Override
        public void close() {
            closeNoExceptions(channel);
        }
        @Override
        public void run() {

            try {

				final SelectionKey key = channel.register(_selector, 0, attachment);// 注意:这里是生成的客户端CHANNEL

				submit(new CreateEndPoint(channel, key));

            } catch (Throwable x) {
                closeNoExceptions(channel);
                LOG.debug(x);
            }
        }
		// *****************************************************************************
    }
    private class CreateEndPoint implements Product, Closeable {

        private final SocketChannel channel;
		private final SelectionKey key;// 注册到本类对应的选择器之后返回的KEY

		public CreateEndPoint(SocketChannel channel, SelectionKey key) {// 会在ACCEPT执行中创建
            this.channel = channel;
            this.key = key;
        }

		// *****************************************************************************
        @Override
		public void run() {
            try {
				createEndPoint(channel, key);
            } catch (Throwable x) {
                failed(x);
            }
        }
        @Override
        public void close() {
            closeNoExceptions(channel);
        }

		// *****************************************************************************
        protected void failed(Throwable failure) {
            closeNoExceptions(channel);
        }
		// *****************************************************************************
    }
	// *************************************************************************************************************
    class Connect implements Runnable {

        private final AtomicBoolean failed = new AtomicBoolean();
        private final SocketChannel channel;
        private final Object attachment;
        private final Scheduler.Task timeout;

        Connect(SocketChannel channel, Object attachment) {
            this.channel = channel;
            this.attachment = attachment;
            this.timeout = ManagedSelector.this._selectorManager.getScheduler().schedule(new ConnectTimeout(this), ManagedSelector.this._selectorManager.getConnectTimeout(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void run() {
            try {
                channel.register(_selector, SelectionKey.OP_CONNECT, this);
            } catch (Throwable x) {
                failed(x);
            }
        }
        private void failed(Throwable failure) {
            if (failed.compareAndSet(false, true)) {
                timeout.cancel();
                closeNoExceptions(channel);
                ManagedSelector.this._selectorManager.connectionFailed(channel, failure, attachment);
            }
        }
    }

    private class ConnectTimeout implements Runnable {

        private final Connect connect;

        private ConnectTimeout(Connect connect) {
            this.connect = connect;
        }

        @Override
        public void run() {

            SocketChannel channel = connect.channel;

            if (channel.isConnectionPending()) {//tells whether or not a connection operation is in progress on this channel.
                connect.failed(new SocketTimeoutException("Connect Timeout"));
            }
        }
    }

    private class CloseEndPoints implements Runnable {

        private final CountDownLatch _latch = new CountDownLatch(1);

        private CountDownLatch _allClosed;

        @Override
        public void run() {

            List<EndPoint> end_points = new ArrayList<>();

			for (SelectionKey key : _selector.keys()) {

                if (key.isValid()) {

					Object attachment = key.attachment();

                    if (attachment instanceof EndPoint) {
                        end_points.add((EndPoint)attachment);
					}
                }
            }

            int size = end_points.size();

            _allClosed = new CountDownLatch(size);

			_latch.countDown();

            for (EndPoint endp : end_points) {
				submit(new EndPointCloser(endp, _allClosed));
			}
        }

		public boolean await(long timeout) {
            try {
                return _latch.await(timeout, TimeUnit.MILLISECONDS) && _allClosed.await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException x) {
                return false;
            }
        }
    }

	private class EndPointCloser implements Product {// RUNNABLE
        private final EndPoint _endPoint;
        private final CountDownLatch _latch;
        private EndPointCloser(EndPoint endPoint, CountDownLatch latch) {
            _endPoint = endPoint;
            _latch = latch;
        }
        @Override
        public void run() {
			closeNoExceptions(_endPoint.getConnection());//
            _latch.countDown();
        }
    }

	private class CloseSelector implements Runnable {//
		private CountDownLatch _latch = new CountDownLatch(1);
        @Override
        public void run() {
            Selector selector = _selector;
            _selector = null;
			closeNoExceptions(selector);//
			_latch.countDown();//
        }

		public boolean await(long timeout) {//
            try {
				return _latch.await(timeout, TimeUnit.MILLISECONDS);//
            } catch (InterruptedException x) {
                return false;
            }
        }
    }
}
