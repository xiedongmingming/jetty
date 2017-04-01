package org.eclipse.jetty.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.DateGenerator;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpGenerator;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.Uptime;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.component.Graceful;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Locker;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ShutdownThread;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool.SizedThreadPool;

//实现类: org.eclipse.jetty.server.Server--有三个组件: QueuedThreadPool、ServerConnector、HandlerCollection
//
//1、
//开始BEAN: org.eclipse.jetty.util.thread.QueuedThreadPool
//结束BEAN: org.eclipse.jetty.util.thread.QueuedThreadPool
//
//2、
//开始BEAN: org.eclipse.jetty.server.handler.HandlerCollection
//	
//	实现类: org.eclipse.jetty.server.handler.HandlerCollection--有两个组件: ServletContextHandler、DefaultHandler
//		
//		开始BEAN: org.eclipse.jetty.servlet.ServletContextHandler
//		
//			实现类: org.eclipse.jetty.servlet.ServletContextHandler
//				
//				开始BEAN: org.eclipse.jetty.servlet.ServletHandler
//				
//					实现类: org.eclipse.jetty.servlet.ServletHandler
//						开始BEAN: org.eclipse.jetty.servlet.ServletHolder
//						结束BEAN: org.eclipse.jetty.servlet.ServletHolder
//						开始BEAN: org.eclipse.jetty.servlet.ServletMapping
//						结束BEAN: org.eclipse.jetty.servlet.ServletMapping
//						开始BEAN: org.eclipse.jetty.servlet.ServletHolder
//						结束BEAN: org.eclipse.jetty.servlet.ServletHolder
//						开始BEAN: org.eclipse.jetty.servlet.ServletMapping
//						结束BEAN: org.eclipse.jetty.servlet.ServletMapping
//						开始BEAN: org.eclipse.jetty.servlet.ServletHolder
//						结束BEAN: org.eclipse.jetty.servlet.ServletHolder
//						开始BEAN: org.eclipse.jetty.servlet.ServletMapping
//						结束BEAN: org.eclipse.jetty.servlet.ServletMapping
//					
//				结束BEAN: org.eclipse.jetty.servlet.ServletHandler
//				
//		结束BEAN: org.eclipse.jetty.servlet.ServletContextHandler
//		
//		开始BEAN: org.eclipse.jetty.server.handler.DefaultHandler
//					
//			实现类: org.eclipse.jetty.server.handler.DefaultHandler
//		
//		结束BEAN: org.eclipse.jetty.server.handler.DefaultHandler
//	
//结束BEAN: org.eclipse.jetty.server.handler.HandlerCollection
//
//3、
//实现类: org.eclipse.jetty.server.ServerConnector--有七个组件
//	
//	1、
//	开始BEAN: org.eclipse.jetty.server.Server
//	结束BEAN: org.eclipse.jetty.server.Server
//	
//	2、
//	开始BEAN: org.eclipse.jetty.util.thread.QueuedThreadPool
//	结束BEAN: org.eclipse.jetty.util.thread.QueuedThreadPool
//	
//	3、
//	开始BEAN: org.eclipse.jetty.util.thread.ScheduledExecutorScheduler
//	结束BEAN: org.eclipse.jetty.util.thread.ScheduledExecutorScheduler
//	
//	4、
//	开始BEAN: org.eclipse.jetty.io.ArrayByteBufferPool
//	结束BEAN: org.eclipse.jetty.io.ArrayByteBufferPool
//	
//	5、
//	开始BEAN: org.eclipse.jetty.server.HttpConnectionFactory
//	
//		实现类: org.eclipse.jetty.server.HttpConnectionFactory--有一个组件
//		
//			开始BEAN: org.eclipse.jetty.server.HttpConfiguration
//			结束BEAN: org.eclipse.jetty.server.HttpConfiguration
//			
//	结束BEAN: org.eclipse.jetty.server.HttpConnectionFactory
//	
//	6、		
//	开始BEAN: org.eclipse.jetty.server.ServerConnector$ServerConnectorManager
//				
//		实现类: org.eclipse.jetty.server.ServerConnector$ServerConnectorManager--有两个组件
//				
//			开始BEAN: org.eclipse.jetty.io.ManagedSelector
//			结束BEAN: org.eclipse.jetty.io.ManagedSelector
//			开始BEAN: org.eclipse.jetty.io.ManagedSelector
//			结束BEAN: org.eclipse.jetty.io.ManagedSelector
//					
//	结束BEAN: org.eclipse.jetty.server.ServerConnector$ServerConnectorManager
//	
//	7、
//	开始BEAN: sun.nio.ch.ServerSocketChannelImpl
//	结束BEAN: sun.nio.ch.ServerSocketChannelImpl

public class Server extends HandlerWrapper implements Attributes {

	private static final Logger LOG = Log.getLogger(Server.class);// 注意这里是JETTY自带的LOG

	private final AttributesMap _attributes = new AttributesMap();
	
    private SessionIdManager _sessionIdManager;
	
	private boolean _stopAtShutdown;
    private boolean _dumpAfterStart = false;
    private boolean _dumpBeforeStop = false;

    private final Locker _dateLocker = new Locker();
	
    private volatile DateField _dateField;

	// **************************************************************************************************************
	private final ThreadPool _threadPool;// 该服务所具有的线程池(默认为:QueuedThreadPool)
	private final List<Connector> _connectors = new CopyOnWriteArrayList<>();// 该服务所具有的所有连接器(默认为:ServerConnector)
	// **************************************************************************************************************
	// 构造函数:
    public Server() {
        this((ThreadPool) null);
    }
	public Server(@Name("port") int port) {

        this((ThreadPool) null);

		ServerConnector connector = new ServerConnector(this);

        connector.setPort(port);

        setConnectors(new Connector[]{connector});
    }
	public Server(@Name("address") InetSocketAddress addr) {

        this((ThreadPool)null);

        ServerConnector connector = new ServerConnector(this);

        connector.setHost(addr.getHostName());
        connector.setPort(addr.getPort());

        setConnectors(new Connector[]{connector});
    }
	public Server(@Name("threadpool") ThreadPool pool) {// 具体实现类

		_threadPool = pool != null ? pool : new QueuedThreadPool();

        addBean(_threadPool);

		setServer(this);// 向上层报告
    }
	// **************************************************************************************************************
	private RequestLog _requestLog;
    public RequestLog getRequestLog() {
        return _requestLog;
    }
    public void setRequestLog(RequestLog requestLog) {
        updateBean(_requestLog, requestLog);
        _requestLog = requestLog;
    }
	//************************************************************
    public static String getVersion() {
        return Jetty.VERSION;
    }
	//************************************************************
    @Override
    public void setStopTimeout(long stopTimeout) {
        super.setStopTimeout(stopTimeout);
    }
	// ************************************************************
	public void setStopAtShutdown(boolean stop) {//
		if (stop) {
			if (!_stopAtShutdown) {
				if (isStarted()) {
                    ShutdownThread.register(this);
				}
            }
        } else {
            ShutdownThread.deregister(this);
		}
        _stopAtShutdown = stop;
    }
	public boolean getStopAtShutdown() {
        return _stopAtShutdown;
    }
	//*****************************************************************************
	// 服务的连接器相关
    public void addConnector(Connector connector) {
        if (connector.getServer() != this) {
            throw new IllegalArgumentException("connector " + connector + " cannot be shared among server " + connector.getServer() + " and server " + this);
        }
		if (_connectors.add(connector)) {
            addBean(connector);
		}
    }
    public void removeConnector(Connector connector) {
        if (_connectors.remove(connector)) {
			removeBean(connector);
		}
    }	
	public void setConnectors(Connector[] connectors) {// 为服务添加连接器
        if (connectors != null) {
            for (Connector connector : connectors) {
				if (connector.getServer() != this) {// 连接器所属的服务必须一致
                	throw new IllegalArgumentException("connector " + connector + " cannot be shared among server " + connector.getServer() + " and server " + this);
                }
            }
        }
        Connector[] oldConnectors = getConnectors();
		updateBeans(oldConnectors, connectors);// 进行BEAN的更新--ContainerLifeCycle
        _connectors.removeAll(Arrays.asList(oldConnectors));
        if (connectors != null) {
			_connectors.addAll(Arrays.asList(connectors));
		}
    }
    public Connector[] getConnectors() {
        List<Connector> connectors = new ArrayList<>(_connectors);
        return connectors.toArray(new Connector[connectors.size()]);
    }
	//*****************************************************************************
    public ThreadPool getThreadPool() {
        return _threadPool;
    }
    public boolean isDumpAfterStart() {
        return _dumpAfterStart;
    }
    public void setDumpAfterStart(boolean dumpAfterStart) {
        _dumpAfterStart = dumpAfterStart;
    }
    public boolean isDumpBeforeStop() {
        return _dumpBeforeStop;
    }
    public void setDumpBeforeStop(boolean dumpBeforeStop) {
        _dumpBeforeStop = dumpBeforeStop;
    }
    public HttpField getDateField() {
        long now = System.currentTimeMillis();
        long seconds = now/1000;
        DateField df = _dateField;

        if (df == null || df._seconds != seconds) {
            try(Locker.Lock lock = _dateLocker.lock()) {
                df = _dateField;
                if (df == null || df._seconds != seconds) {
                    HttpField field = new PreEncodedHttpField(HttpHeader.DATE, DateGenerator.formatDate(now));
                    _dateField = new DateField(seconds,field);
                    return field;
                }
            }
        }
        return df._dateField;
    }
    @Override
	protected void doStart() throws Exception {// 执行启动操作

		if (getStopAtShutdown()) {
            ShutdownThread.register(this);
		}

		ShutdownMonitor.register(this);
		ShutdownMonitor.getInstance().start();

        LOG.info("jetty-" + getVersion());

        if (!Jetty.STABLE) {

            LOG.warn("THIS IS NOT A STABLE RELEASE! DO NOT USE IN PRODUCTION!");

			LOG.warn("download a stable release from http://download.eclipse.org/jetty/");
        }

        HttpGenerator.setJettyVersion(HttpConfiguration.SERVER_VERSION);

        MultiException mex = new MultiException();

		SizedThreadPool pool = getBean(SizedThreadPool.class);// 具体实现为:QueuedThreadPool

		System.out.println("线程池的最大线程数量为: " + pool.getMaxThreads());// 200

        int max = pool == null ? -1 : pool.getMaxThreads();
        int selectors = 0;
        int acceptors = 0;
		
        if (mex.size() == 0) {
			for (Connector connector : _connectors) {// 就一个:ServerConnector
                if (connector instanceof AbstractConnector) {
					acceptors += ((AbstractConnector) connector).getAcceptors();// 1个
				}
                if (connector instanceof ServerConnector) {
					selectors += ((ServerConnector) connector).getSelectorManager().getSelectorCount();// 2个
				}
            }
        }

		int needed = 1 + selectors + acceptors;// 表示需要的线程数量
        if (max > 0 && needed > max) {
            throw new IllegalStateException(String.format("insufficient threads: max=%d < needed(acceptors=%d + selectors=%d + request=1)", max, acceptors, selectors));
		}
        try {
			super.doStart();//
        } catch(Throwable e) {
            mex.add(e);
        }

		System.out.println("************************************************************************************");
		System.out.println("启动所有的连接器");
		for (Connector connector : _connectors) {// 最后启动连接器(就一个)
            try {
				connector.start();// ServerConnector
            } catch(Throwable e) {
                mex.add(e);
            }
        }
		System.out.println("连接器结束");

        if (isDumpAfterStart()) {
            dumpStdErr();
		}

        mex.ifExceptionThrow();

		LOG.info(String.format("started @%dms", Uptime.getUptime()));
    }
    @Override
	protected void start(LifeCycle l) throws Exception {// 用于运行某个组件--例如服务SERVER中包含的组件线程池等的启动都是通过该函数来完成的
		if (!(l instanceof Connector)) {// 暂时不启动连接器
			super.start(l);
		}
    }

	@Override
    protected void doStop() throws Exception {
		if (isDumpBeforeStop()) {
			dumpStdErr();
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("doStop {}", this);
		}

		MultiException mex = new MultiException();

        // list if graceful futures
        List<Future<Void>> futures = new ArrayList<>();

        // First close the network connectors to stop accepting new connections
		for (Connector connector : _connectors) {
			futures.add(connector.shutdown());
		}

        // Then tell the contexts that we are shutting down
        Handler[] gracefuls = getChildHandlersByClass(Graceful.class);
		for (Handler graceful : gracefuls) {
			futures.add(((Graceful) graceful).shutdown());
		}

        // Shall we gracefully wait for zero connections?
        long stopTimeout = getStopTimeout();
        if (stopTimeout > 0) {
            long stop_by = System.currentTimeMillis()+stopTimeout;
			if (LOG.isDebugEnabled()) {
				LOG.debug("Graceful shutdown {} by ", this, new Date(stop_by));
			}

            // Wait for shutdowns
            for (Future<Void> future: futures) {
                try {
					if (!future.isDone()) {
						future.get(Math.max(1L, stop_by - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
					}
                } catch (Exception e) {
                    mex.add(e);
                }
            }
        }

        // Cancel any shutdowns not done
        for (Future<Void> future: futures)
            if (!future.isDone())
                future.cancel(true);

        // Now stop the connectors (this will close existing connections)
        for (Connector connector : _connectors) {
            try {
                connector.stop();
            } catch (Throwable e) {
                mex.add(e);
            }
        }

        // And finally stop everything else
        try {
            super.doStop();
        } catch (Throwable e) {
            mex.add(e);
        }

        if (getStopAtShutdown())
            ShutdownThread.deregister(this);

        //Unregister the Server with the handler thread for receiving
        //remote stop commands as we are stopped already
        ShutdownMonitor.deregister(this);

        mex.ifExceptionThrow();
    }

	// *******************************************************************************************************************************
	public void handle(HttpChannel connection) throws IOException, ServletException { // 处理所有的请求

		// 参数的实际类型: HttpChannelOverHttp
		
		// 该函数在HTTPCHANNEL中调用

		System.out.println("请求处理过程: 1. Server");

		// 每一次请求都会生成对应的HTTP通道--HttpChannelOverHttp

		final String target = connection.getRequest().getPathInfo();// 请求的路径--主机后面的资源路径

		final Request request = connection.getRequest();// 每次都不一样
		final Response response = connection.getResponse();// 每次都不一样

		if (HttpMethod.OPTIONS.is(request.getMethod()) || "*".equals(target)) {// 表示是OPTIONS请求或目标为统配请求
            if (!HttpMethod.OPTIONS.is(request.getMethod())) {
				response.sendError(HttpStatus.BAD_REQUEST_400);// 统配请求
			}
            handleOptions(request, response);
            if (!request.isHandled()) {
                handle(target, request, request, response);
			}
        } else {
			handle(target, request, request, response);// -->HandlerWrapper.handle
		}
	}
    protected void handleOptions(Request request,Response response) throws IOException {

    }
	public void handleAsync(HttpChannel connection) throws IOException, ServletException {// 处理异步请求

        final HttpChannelState state = connection.getRequest().getHttpChannelState();
        final AsyncContextEvent event = state.getAsyncContextEvent();

        final Request baseRequest=connection.getRequest();
        final String path=event.getPath();

        if (path != null) {
            //this is a dispatch with a path
            ServletContext context=event.getServletContext();
            String query = baseRequest.getQueryString();
            baseRequest.setURIPathQuery(URIUtil.addPaths(context == null ? null : context.getContextPath(),  path));
            HttpURI uri = baseRequest.getHttpURI();
            baseRequest.setPathInfo(uri.getDecodedPath());
            if (uri.getQuery()!=null)
                baseRequest.mergeQueryParameters(query, uri.getQuery(), true); //we have to assume dispatch path and query are UTF8
        }

        final String target=baseRequest.getPathInfo();
        final HttpServletRequest request=(HttpServletRequest)event.getSuppliedRequest();
        final HttpServletResponse response=(HttpServletResponse)event.getSuppliedResponse();

        if (LOG.isDebugEnabled()) {
            LOG.debug(request.getDispatcherType() + " "+request.getMethod()+" "+target+" on "+connection);
            handle(target, baseRequest, request, response);
            LOG.debug("RESPONSE "+target+"  "+connection.getResponse().getStatus());
        } else {
            handle(target, baseRequest, request, response);
		}
    }
	public void join() throws InterruptedException {// 主线程会在最后调用该函数阻塞在线程池上
        getThreadPool().join();
    }
    public SessionIdManager getSessionIdManager() {
        return _sessionIdManager;
    }
    public void setSessionIdManager(SessionIdManager sessionIdManager) {
        updateBean(_sessionIdManager,sessionIdManager);
        _sessionIdManager=sessionIdManager;
    }
	//***************************************************************************************
	//
    @Override
	public void clearAttributes() {//
        Enumeration<String> names = _attributes.getAttributeNames();
        while (names.hasMoreElements()) {
            removeBean(_attributes.getAttribute(names.nextElement()));
		}
        _attributes.clearAttributes();
    }
    @Override
    public Object getAttribute(String name) {
        return _attributes.getAttribute(name);
    }
    @Override
    public Enumeration<String> getAttributeNames() {
        return AttributesMap.getAttributeNamesCopy(_attributes);
    }
    @Override
	public void removeAttribute(String name) {//
        Object bean = _attributes.getAttribute(name);
        if (bean != null) {
            removeBean(bean);
		}
        _attributes.removeAttribute(name);
    }
    @Override
	public void setAttribute(String name, Object attribute) {//
        addBean(attribute);
        _attributes.setAttribute(name, attribute);
    }
	//***************************************************************************************
    public URI getURI() {
        NetworkConnector connector = null;
		for (Connector c : _connectors) {//
            if (c instanceof NetworkConnector) {
                connector = (NetworkConnector)c;
                break;
            }
        }
        if (connector == null) {
			return null;
		}
        ContextHandler context = getChildHandlerByClass(ContextHandler.class);//
        try {
            String protocol = connector.getDefaultConnectionFactory().getProtocol();
            String scheme = "http";
            if (protocol.startsWith("SSL-") || protocol.equals("SSL")) {
                scheme = "https";
			}
            String host = connector.getHost();
            if (context != null && context.getVirtualHosts() != null && context.getVirtualHosts().length > 0) {
                host = context.getVirtualHosts()[0];
			}
            if (host == null) {
                host = InetAddress.getLocalHost().getHostAddress();
			}
            String path = context == null ? null : context.getContextPath();
            if (path == null) {
                path = "/";
			}
            return new URI(scheme, null, host, connector.getLocalPort(), path, null, null);
        } catch(Exception e) {
            LOG.warn(e);
            return null;
        }
    }
    @Override
    public String toString() {
        return this.getClass().getName() + "@" + Integer.toHexString(hashCode());
    }
    @Override
    public void dump(Appendable out, String indent) throws IOException {
        dumpBeans(out, indent, Collections.singleton(new ClassLoaderDump(this.getClass().getClassLoader())));
    }

    public static void main(String...args) throws Exception {
        System.err.println(getVersion());
    }

    private static class DateField {
        final long _seconds;
        final HttpField _dateField;
        public DateField(long seconds, HttpField dateField) {
            super();
            _seconds = seconds;
            _dateField = dateField;
        }
    }
}
