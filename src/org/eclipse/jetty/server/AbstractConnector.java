package org.eclipse.jetty.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

public abstract class AbstractConnector extends ContainerLifeCycle implements Connector, Dumpable {

	// 该类将连接器需要用到的各个组件关联起来

	protected final Logger LOG = Log.getLogger(AbstractConnector.class);

	private final Map<String, ConnectionFactory> _factories = new LinkedHashMap<>();// 注册的所有可以协议及其对应的连接工厂(可能多个协议对应同一个工厂)
	private final Server _server;// 表示该连接器所在的服务
	private final Executor _executor;// 默认为服务中的线程池:QueuedThreadPool
	private final Scheduler _scheduler;// 先以服务中的调度器然后采用默认(用于连接超时):ScheduledExecutorScheduler
	private final ByteBufferPool _byteBufferPool;// ArrayByteBufferPool
	private final Thread[] _acceptors;// 每个接收器对应一个线程(会由线程池中的线程来填充)
	private final Set<EndPoint> _endpoints = Collections.newSetFromMap(new ConcurrentHashMap<>());// 保存所有打开的端点
    private final Set<EndPoint> _immutableEndPoints = Collections.unmodifiableSet(_endpoints);
    private volatile CountDownLatch _stopping;
    private long _idleTimeout = 30000;
	private String _defaultProtocol;// 默认协议:HTTP/1.1
    private ConnectionFactory _defaultConnectionFactory;
	private String _name;// ????
	private int _acceptorPriorityDelta;// 初始值--只针对接收器(Acceptor)

	// *******************************************************************************************************
	// 构造函数
    public AbstractConnector(Server server, 
    		Executor executor, 
    		Scheduler scheduler, 
    		ByteBufferPool pool, 
    		int acceptors, 
			ConnectionFactory... factories) {// HttpConnectionFactory

		// ******************************************************
		// 服务组件
		_server = server;// 一定不为空

		// ******************************************************
		// 执行器--默认为服务关联的线程池
		_executor = executor != null ? executor : _server.getThreadPool();// 服务中的线程池--QueuedThreadPool

		// ******************************************************
		// 调度器--为服务关联的调度器(如服务未关联则使用默认)
		// 使用顺序:
		// 1、参数提供的
		// 2、服务中有的
		// 3、默认的
		if (scheduler == null) {
			scheduler = _server.getBean(Scheduler.class);// 默认也是先看服务SERVER是否有
		}
		_scheduler = scheduler != null ? scheduler : new ScheduledExecutorScheduler();// 默认--JETTY实现的

		//******************************************************
		// 缓存池--为服务关联的调度器(如服务未关联则使用默认)
		// 使用顺序:
		// 1、参数提供的
		// 2、服务中有的
		// 3、默认的
        if (pool == null) {
            pool = _server.getBean(ByteBufferPool.class);
		}
		_byteBufferPool = pool != null ? pool : new ArrayByteBufferPool();// 默认

		//******************************************************
		// 在此添加了四个BEAN
		// 第一个BEAN
		addBean(_server, false);// 第二个参数表示不对该BEAN进行管理
		// 第二个BEAN
        addBean(_executor);
        if (executor == null) {
			unmanage(_executor); // inherited from server--由服务来管理(自己不管理)
		}
		// 第三个BEAN
        addBean(_scheduler);
		// 第四个BEAN
        addBean(_byteBufferPool);

		for (ConnectionFactory factory : factories) {// 在此添加一个BEAN
            addConnectionFactory(factory);
		}

		// ******************************************************
		int cores = Runtime.getRuntime().availableProcessors();// CPU数量

		if (acceptors < 0) {// 1-4个
            acceptors = Math.max(1, Math.min(4, cores / 8));
		}
        if (acceptors > cores) {
			LOG.warn("acceptors should be <= availableprocessors: " + this);
		}

		System.out.println("CPU核数为: " + cores + " : " + acceptors);

        _acceptors = new Thread[acceptors];
    }

	// *******************************************************************************************************
	// 只实现了其中的一部分
    @Override
    public Server getServer() {
        return _server;
    }
    @Override
    public Executor getExecutor() {
        return _executor;
    }
	@Override
    public Scheduler getScheduler() {
        return _scheduler;
    }
    @Override
    public ByteBufferPool getByteBufferPool() {
        return _byteBufferPool;
    }
	@Override
    public ConnectionFactory getConnectionFactory(String protocol) {
        synchronized (_factories) {
            return _factories.get(StringUtil.asciiToLowerCase(protocol));
        }
    }
	@SuppressWarnings("unchecked")
	@Override
    public <T> T getConnectionFactory(Class<T> factoryType) {
        synchronized (_factories) {
            for (ConnectionFactory f : _factories.values()) {
                if (factoryType.isAssignableFrom(f.getClass())) {
                    return (T)f;
				}
			}
            return null;
        }
    }
	@Override
    public ConnectionFactory getDefaultConnectionFactory() {
        if (isStarted()) {
            return _defaultConnectionFactory;
		}
        return getConnectionFactory(_defaultProtocol);
    }
    @Override
    public Collection<ConnectionFactory> getConnectionFactories() {
        synchronized (_factories) {
            return _factories.values();
        }
    }
	@Override
    public List<String> getProtocols() {
        synchronized (_factories) {
            return new ArrayList<>(_factories.keySet());
        }
    }
    @Override
    public long getIdleTimeout() {
        return _idleTimeout;
    }
	@Override
    public Collection<EndPoint> getConnectedEndPoints() {
        return _immutableEndPoints;
    }
	@Override
    public String getName() {
        return _name;
    }
	//**************************************************************************
    public void setIdleTimeout(long idleTimeout) {
        _idleTimeout = idleTimeout;
    }
	public int getAcceptors() {// 接收器对应的线程数量
        return _acceptors.length;
    }
	//**************************************************************************
    @Override
	protected void doStart() throws Exception {// 4.0.0.3 -- 启动过程

		_defaultConnectionFactory = getConnectionFactory(_defaultProtocol);// 默认--HttpConnectionFactory

		if (_defaultConnectionFactory == null) {// 默认为HTTP
			throw new IllegalStateException("no protocol factory for default protocol: " + _defaultProtocol);
		}

		super.doStart();// 启动容器中的BEAN:ContainerLifeCycle

		_stopping = new CountDownLatch(_acceptors.length);// ????

		System.out.println("接收器的数量: " + _acceptors.length);
		
		for (int i = 0; i < _acceptors.length; i++) {// 连接器启动的最后了
            Acceptor a = new Acceptor(i);
			addBean(a);// ServerConnector--将接收器添加到服务连机器中(这里的BEAN就不在由启动时启动了(而且也不是生命周期对象))
			getExecutor().execute(a);// 使用线程池执行该任务
        }
		LOG.info("started {}", this);// 到此表示整个连接器启动完成
    }
    @Override
    protected void doStop() throws Exception {
        interruptAcceptors();//tell the acceptors we are stopping
        long stopTimeout = getStopTimeout();//if we have a stop timeout
        CountDownLatch stopping = _stopping;
        if (stopTimeout > 0 && stopping != null) {
            stopping.await(stopTimeout, TimeUnit.MILLISECONDS);
		}
        _stopping = null;
        super.doStop();
        for (Acceptor a : getBeans(Acceptor.class)) {
            removeBean(a);
		}
        LOG.info("Stopped {}", this);
    }
	//**************************************************************************
    protected void interruptAcceptors() {
        synchronized (this) {
            for (Thread thread : _acceptors) {
                if (thread != null) {
                    thread.interrupt();
				}
            }
        }
    }
	//**************************************************************************
    @Override
    public Future<Void> shutdown() {
        return new FutureCallback(true);
    }
	//**************************************************************************


    public void join() throws InterruptedException {
        join(0);
    }

    public void join(long timeout) throws InterruptedException {
        synchronized (this) {
			for (Thread thread : _acceptors) {
				if (thread != null) {
					thread.join(timeout);
				}
			}
        }
    }

	// 由服务连接器实现--ServerConnector
	protected abstract void accept(int acceptorID) throws IOException, InterruptedException;// 处理指定编号的接收器

    protected boolean isAccepting() {
        return isRunning();
    }

	public void addConnectionFactory(ConnectionFactory factory) {// 逐个添加:HttpConnectionFactory
        synchronized (_factories) {
            Set<ConnectionFactory> to_remove = new HashSet<>();
			for (String key : factory.getProtocols()) {// 目前就一个连接工厂

				System.out.println("连接工厂协议: " + key);

                key = StringUtil.asciiToLowerCase(key);

                ConnectionFactory old = _factories.remove(key);

				if (old != null) {// 表示存在
                    if (old.getProtocol().equals(_defaultProtocol)) {
                        _defaultProtocol = null;
					}
                    to_remove.add(old);
                }
                _factories.put(key, factory);
            }
            for (ConnectionFactory f : _factories.values()) {//keep factories still referenced
                to_remove.remove(f);
			}
            for (ConnectionFactory old: to_remove) {//remove old factories
                removeBean(old);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} removed {}", this, old);
				}
            }
			// 第五个BEAN
			addBean(factory); // add new bean
            if (_defaultProtocol == null) {
                _defaultProtocol = factory.getProtocol();
			}
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} added {}", this, factory);
			}
        }
    }
    public void addFirstConnectionFactory(ConnectionFactory factory) {
        synchronized (_factories) {
            List<ConnectionFactory> existings = new ArrayList<>(_factories.values());
            _factories.clear();
            addConnectionFactory(factory);
            for (ConnectionFactory existing : existings) {
                addConnectionFactory(existing);
			}
            _defaultProtocol = factory.getProtocol();
        }
    }
    public void addIfAbsentConnectionFactory(ConnectionFactory factory) {
        synchronized (_factories) {
            String key = StringUtil.asciiToLowerCase(factory.getProtocol());
            if (_factories.containsKey(key)) {

            } else {
                _factories.put(key, factory);
                addBean(factory);
                if (_defaultProtocol == null) {
                    _defaultProtocol=factory.getProtocol();
				}

            }
        }
    }
    public ConnectionFactory removeConnectionFactory(String protocol) {
        synchronized (_factories) {
            ConnectionFactory factory = _factories.remove(StringUtil.asciiToLowerCase(protocol));
            removeBean(factory);
            return factory;
        }
    }
    public void setConnectionFactories(Collection<ConnectionFactory> factories) {
        synchronized (_factories) {
            List<ConnectionFactory> existing = new ArrayList<>(_factories.values());
            for (ConnectionFactory factory: existing) {
                removeConnectionFactory(factory.getProtocol());
			}
            for (ConnectionFactory factory: factories) {
                if (factory != null) {
                    addConnectionFactory(factory);
				}
			}
        }
    }
    public int getAcceptorPriorityDelta() {
        return _acceptorPriorityDelta;
    }
    public void setAcceptorPriorityDelta(int acceptorPriorityDelta) {
        int old = _acceptorPriorityDelta;
		System.out.println("优先级初始值: " + old);
        _acceptorPriorityDelta = acceptorPriorityDelta;
		if (old != acceptorPriorityDelta && isStarted()) {// 好像还未开始
            for (Thread thread : _acceptors) {
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, thread.getPriority() - old + acceptorPriorityDelta)));
			}
        }
    }
    public void clearConnectionFactories() {
        synchronized (_factories) {
            _factories.clear();
        }
    }
    public String getDefaultProtocol() {
        return _defaultProtocol;
    }

    public void setDefaultProtocol(String defaultProtocol) {
        _defaultProtocol = StringUtil.asciiToLowerCase(defaultProtocol);
        if (isRunning())
            _defaultConnectionFactory=getConnectionFactory(_defaultProtocol);
    }
    protected boolean handleAcceptFailure(Throwable previous, Throwable current) {
        if (isAccepting()) {
            if (previous == null) {
                LOG.warn(current);
            } else {
                LOG.debug(current);
			} 
            try {
                Thread.sleep(1000);
                return true;
            } catch (Throwable x) {
                return false;
            }
        } else {
            LOG.ignore(current);
            return false;
        }
    }

	private class Acceptor implements Runnable {// 用于接收客户端的连接请求--连接器启动的最后一步创建并提交到连接器的执行器

		private final int _id;// 编号
		private String _name;// 执行线程的名称

        private Acceptor(int id) {
            _id = id;
        }

        @Override
		public void run() {// 由连接器的执行器执行

            final Thread thread = Thread.currentThread();

            String name = thread.getName();

            _name = String.format("%s-acceptor-%d@%x-%s", name, _id, hashCode(), AbstractConnector.this.toString());

			// qtp9624012-14-acceptor-0@10c590-ServerConnector@190fc5b{HTTP/1.1,[http/1.1]}{0.0.0.0:8080}
			// System.out.println("ACCEPTOR对应的线程: " + _name);

            thread.setName(_name);

            int priority = thread.getPriority();

            if (_acceptorPriorityDelta != 0) {
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority + _acceptorPriorityDelta)));
			}
            synchronized (AbstractConnector.this) {
                _acceptors[_id] = thread;
            }
            try {
                Throwable exception = null;
                
				// System.out.println("CONNECTOR是否已经运行: " + isAccepting());
                
				while (isAccepting()) {// 一直执行下去--执行到此时已经在运行了
                    try {
						System.out.println("连接建立过程: 1");
                        accept(_id);
                        exception = null;
                    } catch (Throwable x) {
                        if (handleAcceptFailure(exception, x)) {
                            exception = x;
                        } else {
                            break;
						}
                    }
                }
            } finally {
                thread.setName(name);
                if (_acceptorPriorityDelta != 0) {
                    thread.setPriority(priority);
				}
                synchronized (AbstractConnector.this) {
                    _acceptors[_id] = null;
                }
                CountDownLatch stopping = _stopping;
                if (stopping != null) {
                    stopping.countDown();
				}
            }
        }

        @Override
        public String toString() {
			String name = _name;
            if (name == null) {
                return String.format("acceptor-%d@%x", _id, hashCode());
			}
            return name;
        }

    }
    protected void onEndPointOpened(EndPoint endp) {
        _endpoints.add(endp);
    }
    protected void onEndPointClosed(EndPoint endp) {
        _endpoints.remove(endp);
    }
    public void setName(String name) {
        _name = name;
    }
    @Override
    public String toString() {
        return String.format("%s@%x{%s,%s}", _name == null ? getClass().getSimpleName() : _name, hashCode(), getDefaultProtocol(), getProtocols());
    }
}
