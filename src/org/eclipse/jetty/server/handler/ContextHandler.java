package org.eclipse.jetty.server.handler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.ClassLoaderDump;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.component.DumpableCollection;
import org.eclipse.jetty.util.component.Graceful;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;

public class ContextHandler extends ScopedHandler implements Attributes, Graceful {// 上下文处理器

	// 处理WEB请求的前提
    
	public final static int SERVLET_MAJOR_VERSION = 3;
	public final static int SERVLET_MINOR_VERSION = 1;
    
	public static final Class<?>[] SERVLET_LISTENER_TYPES = new Class[] {ServletContextListener.class,
        ServletContextAttributeListener.class,
        ServletRequestListener.class,
        ServletRequestAttributeListener.class};

    public static final int DEFAULT_LISTENER_TYPE_INDEX = 1;
    public static final int EXTENDED_LISTENER_TYPE_INDEX = 0;


    final private static String __unimplmented="Unimplemented - use org.eclipse.jetty.servlet.ServletContextHandler";

    private static final Logger LOG = Log.getLogger(ContextHandler.class);

    private static final ThreadLocal<Context> __context = new ThreadLocal<Context>();

    private static String __serverInfo = "jetty/" + Server.getVersion();

    public static final String MANAGED_ATTRIBUTES = "org.eclipse.jetty.server.context.ManagedAttributes";

	public static Context getCurrentContext() {
        return __context.get();
    }

	public static ContextHandler getContextHandler(ServletContext context) {
		if (context instanceof ContextHandler.Context) {
            return ((ContextHandler.Context)context).getContextHandler();
		}
        Context c=  getCurrentContext();
		if (c != null) {
            return c.getContextHandler();
		}
        return null;
    }

	public static String getServerInfo() {
        return __serverInfo;
    }

	public static void setServerInfo(String serverInfo) {
        __serverInfo = serverInfo;
    }

	protected Context _scontext;// 代表SERVLET的上下文环境
    private final AttributesMap _attributes;
    private final Map<String, String> _initParams;
	private ClassLoader _classLoader;// 类加载器(可以自行设置)
	private String _contextPath = "/";// 表示该环境所代表的路径

    private String _displayName;

    private Resource _baseResource;
    private MimeTypes _mimeTypes;
    private Map<String, String> _localeEncodingMap;
    private String[] _welcomeFiles;
    private ErrorHandler _errorHandler;
	private String[] _vhosts;// 所有的虚拟主机

    private Logger _logger;
    private boolean _allowNullPathInfo;
    private int _maxFormKeys = Integer.getInteger("org.eclipse.jetty.server.Request.maxFormKeys",-1).intValue();
    private int _maxFormContentSize = Integer.getInteger("org.eclipse.jetty.server.Request.maxFormContentSize",-1).intValue();
	private boolean _compactPath = false;// ????
    private boolean _usingSecurityManager = System.getSecurityManager()!=null;

	private final List<EventListener> _eventListeners = new CopyOnWriteArrayList<>();
	private final List<EventListener> _programmaticListeners = new CopyOnWriteArrayList<>();
	private final List<ServletContextListener> _servletContextListeners = new CopyOnWriteArrayList<>();
	private final List<ServletContextAttributeListener> _servletContextAttributeListeners = new CopyOnWriteArrayList<>();
	private final List<ServletRequestListener> _servletRequestListeners = new CopyOnWriteArrayList<>();
	private final List<ServletRequestAttributeListener> _servletRequestAttributeListeners = new CopyOnWriteArrayList<>();
    private final List<ContextScopeListener> _contextListeners = new CopyOnWriteArrayList<>();
    private final List<EventListener> _durableListeners = new CopyOnWriteArrayList<>();
    private Map<String, Object> _managedAttributes;
    private String[] _protectedTargets;
    private final CopyOnWriteArrayList<AliasCheck> _aliasChecks = new CopyOnWriteArrayList<ContextHandler.AliasCheck>();

    public enum Availability { UNAVAILABLE,STARTING,AVAILABLE,SHUTDOWN,};
    private volatile Availability _availability;

	// ***************************************************************************
	public ContextHandler() {
		this(null, null, null);
    }
	protected ContextHandler(Context context) {
		this(context, null, null);
    }
	public ContextHandler(String contextPath) {
		this(null, null, contextPath);
    }
	public ContextHandler(HandlerContainer parent, String contextPath) {
		this(null, parent, contextPath);
    }

	private ContextHandler(Context context, HandlerContainer parent, String contextPath) {// 默认参数都为空
		_scontext = context == null ? new Context() : context;
        _attributes = new AttributesMap();
        _initParams = new HashMap<String, String>();
        addAliasCheck(new ApproveNonExistentDirectoryAliases());
		if (File.separatorChar == '/') {
            addAliasCheck(new AllowSymLinkAliasChecker());
		}
		if (contextPath != null) {
            setContextPath(contextPath);
		}
		if (parent instanceof HandlerWrapper) {
            ((HandlerWrapper)parent).setHandler(this);
		} else if (parent instanceof HandlerCollection) {
            ((HandlerCollection)parent).addHandler(this);
		}
    }
	// ***************************************************************************

    @Override
	public void dump(Appendable out, String indent) throws IOException {
		dumpBeans(out, indent,
                Collections.singletonList(new ClassLoaderDump(getClassLoader())),
				Collections.singletonList(new DumpableCollection("Handler attributes " + this, ((AttributesMap) getAttributes()).getAttributeEntrySet())),
				Collections.singletonList(new DumpableCollection("Context attributes " + this, getServletContext().getAttributeEntrySet())),
				Collections.singletonList(new DumpableCollection("Initparams " + this, getInitParams().entrySet()))
                );
    }

	public Context getServletContext() {
        return _scontext;
    }

	public boolean getAllowNullPathInfo() {
        return _allowNullPathInfo;
    }

	public void setAllowNullPathInfo(boolean allowNullPathInfo) {
        _allowNullPathInfo = allowNullPathInfo;
    }
    @Override
	public void setServer(Server server) {
        super.setServer(server);
        if (_errorHandler != null)
            _errorHandler.setServer(server);
    }
	public boolean isUsingSecurityManager() {
        return _usingSecurityManager;
    }
	public void setUsingSecurityManager(boolean usingSecurityManager) {
        _usingSecurityManager = usingSecurityManager;
    }
	public void setVirtualHosts(String[] vhosts) {
		if (vhosts == null) {
            _vhosts = vhosts;
		} else {
            _vhosts = new String[vhosts.length];
			for (int i = 0; i < vhosts.length; i++) {
				_vhosts[i] = normalizeHostname(vhosts[i]);
			}
        }
    }
	public void addVirtualHosts(String[] virtualHosts) {
		if (virtualHosts == null) {
            return;
		} else {
            List<String> currentVirtualHosts = null;
			if (_vhosts != null) {
                currentVirtualHosts = new ArrayList<String>(Arrays.asList(_vhosts));
			} else {
                currentVirtualHosts = new ArrayList<String>();
            }
			for (int i = 0; i < virtualHosts.length; i++) {
                String normVhost = normalizeHostname(virtualHosts[i]);
				if (!currentVirtualHosts.contains(normVhost)) {
                    currentVirtualHosts.add(normVhost);
                }
            }
            _vhosts = currentVirtualHosts.toArray(new String[0]);
        }
    }
	public void removeVirtualHosts(String[] virtualHosts) {
		if (virtualHosts == null) {
			return;
		} else if (_vhosts == null || _vhosts.length == 0) {
			return;
		} else {
            List<String> existingVirtualHosts = new ArrayList<String>(Arrays.asList(_vhosts));
			for (int i = 0; i < virtualHosts.length; i++) {
                String toRemoveVirtualHost = normalizeHostname(virtualHosts[i]);
				if (existingVirtualHosts.contains(toRemoveVirtualHost)) {
                    existingVirtualHosts.remove(toRemoveVirtualHost);
                }
            }
			if (existingVirtualHosts.isEmpty()) {
				_vhosts = null;
			} else {
                _vhosts = existingVirtualHosts.toArray(new String[0]);
            }
        }
    }
	public String[] getVirtualHosts() {// 获取所有的虚拟主机
        return _vhosts;
    }
    @Override
	public Object getAttribute(String name) {
        return _attributes.getAttribute(name);
    }
    @Override
	public Enumeration<String> getAttributeNames() {
        return AttributesMap.getAttributeNamesCopy(_attributes);
    }
	public Attributes getAttributes() {
        return _attributes;
    }
	public ClassLoader getClassLoader() {
        return _classLoader;
    }
	public String getClassPath() {
		if (_classLoader == null || !(_classLoader instanceof URLClassLoader)) {
			return null;
		}
        URLClassLoader loader = (URLClassLoader)_classLoader;
        URL[] urls = loader.getURLs();
        StringBuilder classpath = new StringBuilder();
		for (int i = 0; i < urls.length; i++) {
			try {
                Resource resource = newResource(urls[i]);
                File file = resource.getFile();
				if (file != null && file.exists()) {
					if (classpath.length() > 0) {
						classpath.append(File.pathSeparatorChar);
					}
                    classpath.append(file.getAbsolutePath());
                }
			} catch (IOException e) {
                LOG.debug(e);
            }
        }
		if (classpath.length() == 0) {
			return null;
		}
        return classpath.toString();
    }
	public String getContextPath() {
        return _contextPath;
    }
	public String getInitParameter(String name) {
        return _initParams.get(name);
    }
	public String setInitParameter(String name, String value) {
        return _initParams.put(name,value);
    }
	public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(_initParams.keySet());
    }
	public Map<String, String> getInitParams() {
        return _initParams;
    }
	public String getDisplayName() {
        return _displayName;
    }
	public EventListener[] getEventListeners() {
        return _eventListeners.toArray(new EventListener[_eventListeners.size()]);
    }
	public void setEventListeners(EventListener[] eventListeners) {
        _contextListeners.clear();
        _servletContextListeners.clear();
        _servletContextAttributeListeners.clear();
        _servletRequestListeners.clear();
        _servletRequestAttributeListeners.clear();
        _eventListeners.clear();

        if (eventListeners!=null)
            for (EventListener listener : eventListeners)
                addEventListener(listener);
    }
	public void addEventListener(EventListener listener) {
        _eventListeners.add(listener);

		if (!(isStarted() || isStarting())) {
			_durableListeners.add(listener);
		}

		if (listener instanceof ContextScopeListener) {
			_contextListeners.add((ContextScopeListener) listener);
		}

		if (listener instanceof ServletContextListener) {
			_servletContextListeners.add((ServletContextListener) listener);
		}

		if (listener instanceof ServletContextAttributeListener) {
			_servletContextAttributeListeners.add((ServletContextAttributeListener) listener);
		}

		if (listener instanceof ServletRequestListener) {
			_servletRequestListeners.add((ServletRequestListener) listener);
		}

		if (listener instanceof ServletRequestAttributeListener) {
			_servletRequestAttributeListeners.add((ServletRequestAttributeListener) listener);
		}
    }
	public void removeEventListener(EventListener listener) {
        _eventListeners.remove(listener);

		if (listener instanceof ContextScopeListener) {
			_contextListeners.remove(listener);
		}

		if (listener instanceof ServletContextListener) {
			_servletContextListeners.remove(listener);
		}

		if (listener instanceof ServletContextAttributeListener) {
			_servletContextAttributeListeners.remove(listener);
		}

		if (listener instanceof ServletRequestListener) {
			_servletRequestListeners.remove(listener);
		}

		if (listener instanceof ServletRequestAttributeListener) {
			_servletRequestAttributeListeners.remove(listener);
		}
    }

    /* ------------------------------------------------------------ */
    /**
     * Apply any necessary restrictions on a programmatic added listener.
     *
     * @param listener the programmatic listener to add
     */
	protected void addProgrammaticListener(EventListener listener) {
        _programmaticListeners.add(listener);
    }

	protected boolean isProgrammaticListener(EventListener listener) {
        return _programmaticListeners.contains(listener);
    }



    /* ------------------------------------------------------------ */
    /**
     * @return true if this context is accepting new requests
     */
    @ManagedAttribute("true for graceful shutdown, which allows existing requests to complete")
	public boolean isShutdown() {
		switch (_availability) {
            case SHUTDOWN:
                return true;
            default:
                return false;
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * Set shutdown status. This field allows for graceful shutdown of a context. A started context may be put into non accepting state so that existing
     * requests can complete, but no new requests are accepted.
     *
     */
    @Override
	public Future<Void> shutdown() {
        _availability = isRunning() ? Availability.SHUTDOWN : Availability.UNAVAILABLE;
        return new FutureCallback(true);
    }

    /* ------------------------------------------------------------ */
    /**
     * @return false if this context is unavailable (sends 503)
     */
	public boolean isAvailable() {
        return _availability==Availability.AVAILABLE;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set Available status.
     * @param available true to set as enabled
     */
	public void setAvailable(boolean available) {
		synchronized (this) {
            if (available && isRunning())
                _availability = Availability.AVAILABLE;
            else if (!available || !isRunning())
                _availability = Availability.UNAVAILABLE;
        }
    }
	public Logger getLogger() {
        return _logger;
    }
	public void setLogger(Logger logger) {
        _logger = logger;
    }
    @Override
	protected void doStart() throws Exception {

        _availability = Availability.STARTING;

		if (_contextPath == null) {
			throw new IllegalStateException("Null contextPath");
		}

		if (_logger == null) {
			_logger = Log.getLogger(getDisplayName() == null ? getContextPath() : getDisplayName());
		}

        ClassLoader old_classloader = null;
        Thread current_thread = null;
        Context old_context = null;

        _attributes.setAttribute("org.eclipse.jetty.server.Executor",getServer().getThreadPool());

		if (_mimeTypes == null) {
			_mimeTypes = new MimeTypes();
		}
        
		try {
            // Set the classloader, context and enter scope
			if (_classLoader != null) {
                current_thread = Thread.currentThread();
                old_classloader = current_thread.getContextClassLoader();
                current_thread.setContextClassLoader(_classLoader);
            }
            old_context = __context.get();
            __context.set(_scontext);
            enterScope(null, getState());

            // defers the calling of super.doStart()
            startContext();

            _availability = Availability.AVAILABLE;
            LOG.info("Started {}", this);
		} finally {
			if (_availability == Availability.STARTING) {
				_availability = Availability.UNAVAILABLE;
			}
            exitScope(null);
            __context.set(old_context);
            // reset the classloader
			if (_classLoader != null && current_thread != null) {
				current_thread.setContextClassLoader(old_classloader);
			}
        }
    }
	protected void startContext() throws Exception {
        String managedAttributes = _initParams.get(MANAGED_ATTRIBUTES);
		if (managedAttributes != null) {
			addEventListener(new ManagedAttributeListener(this, StringUtil.csvSplit(managedAttributes)));
		}
        super.doStart();
		if (!_servletContextListeners.isEmpty()) {
            ServletContextEvent event = new ServletContextEvent(_scontext);
			for (ServletContextListener listener : _servletContextListeners) {
				callContextInitialized(listener, event);
			}
        }
    }

	protected void stopContext() throws Exception {
        super.doStop();
		if (!_servletContextListeners.isEmpty()) {
            ServletContextEvent event = new ServletContextEvent(_scontext);
			for (int i = _servletContextListeners.size(); i-- > 0;) {
				callContextDestroyed(_servletContextListeners.get(i), event);
			}
        }
    }

	protected void callContextInitialized(ServletContextListener l, ServletContextEvent e) {
        l.contextInitialized(e);
    }

	protected void callContextDestroyed(ServletContextListener l, ServletContextEvent e) {
        l.contextDestroyed(e);
    }

    @Override
	protected void doStop() throws Exception {

        _availability = Availability.UNAVAILABLE;

        ClassLoader old_classloader = null;
        ClassLoader old_webapploader = null;
        Thread current_thread = null;
        Context old_context = __context.get();
        enterScope(null,"doStop");
        __context.set(_scontext);
		try {
			if (_classLoader != null) {
                old_webapploader = _classLoader;
                current_thread = Thread.currentThread();
                old_classloader = current_thread.getContextClassLoader();
                current_thread.setContextClassLoader(_classLoader);
            }

            stopContext();

            setEventListeners(_durableListeners.toArray(new EventListener[_durableListeners.size()]));
            _durableListeners.clear();

			if (_errorHandler != null) {
				_errorHandler.stop();
			}

			for (EventListener l : _programmaticListeners) {
                removeEventListener(l);
				if (l instanceof ContextScopeListener) {
					try {
						((ContextScopeListener) l).exitScope(_scontext, null);
					} catch (Throwable e) {
                        LOG.warn(e);
                    }
                }
            }
            _programmaticListeners.clear();
		} finally {
            __context.set(old_context);
            exitScope(null);
            LOG.info("Stopped {}", this);
			if ((old_classloader == null || (old_classloader != old_webapploader)) && current_thread != null) {
				current_thread.setContextClassLoader(old_classloader);
			}
        }

        _scontext.clearAttributes();
    }

	public boolean checkVirtualHost(final Request baseRequest) {

		if (_vhosts != null && _vhosts.length > 0) {

			String vhost = normalizeHostname(baseRequest.getServerName());

            boolean match = false;
            boolean connectorName = false;
            boolean connectorMatch = false;

			for (String contextVhost : _vhosts) {
				if (contextVhost == null || contextVhost.length() == 0) {
					continue;
				}
                char c=contextVhost.charAt(0);
				switch (c) {
                    case '*':
					if (contextVhost.startsWith("*.")) {
						match = match || contextVhost.regionMatches(true, 2, vhost, vhost.indexOf(".") + 1,
								contextVhost.length() - 2);
					}
                        break;
                    case '@':
                        connectorName=true;
                        String name=baseRequest.getHttpChannel().getConnector().getName();
                        boolean m=name!=null && contextVhost.length()==name.length()+1 && contextVhost.endsWith(name);
                        match = match || m;
                        connectorMatch = connectorMatch || m;
                        break;
                    default:
                        match = match || contextVhost.equalsIgnoreCase(vhost);
                }

            }
			if (!match || connectorName && !connectorMatch) {
				return false;
			}
        }
        return true;
    }

	public boolean checkContextPath(String uri) {
		if (_contextPath.length() > 1) {// 表示不是根环境
			if (!uri.startsWith(_contextPath)) {
				return false;
			}
			if (uri.length() > _contextPath.length() && uri.charAt(_contextPath.length()) != '/') {
				return false;
			}
        }
        return true;
    }

	public boolean checkContext(final String target, final Request baseRequest, final HttpServletResponse response)
			throws IOException {

		DispatcherType dispatch = baseRequest.getDispatcherType();

		if (!checkVirtualHost(baseRequest)) {// check the vhosts
			return false;
		}

		if (!checkContextPath(target)) {
			return false;
		}

		if (!_allowNullPathInfo && _contextPath.length() == target.length() && _contextPath.length() > 1) {
            baseRequest.setHandled(true);
			if (baseRequest.getQueryString() != null) {
				response.sendRedirect(URIUtil.addPaths(baseRequest.getRequestURI(), URIUtil.SLASH) + "?" + baseRequest.getQueryString());
			} else {
				response.sendRedirect(URIUtil.addPaths(baseRequest.getRequestURI(), URIUtil.SLASH));
			}
            return false;
        }

		switch (_availability) {
            case SHUTDOWN:
            case UNAVAILABLE:
                baseRequest.setHandled(true);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return false;
            default:
			if ((DispatcherType.REQUEST.equals(dispatch) && baseRequest.isHandled())) {
				return false;
			}
        }

        return true;
    }

	// **********************************************************************************************************
	// 起到拦截器的作用
    @Override
    public void doScope(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException     {

		// 父类中调用:ScopedHandler.handle

        Context old_context = null;
        String old_context_path = null;
        String old_servlet_path = null;
        String old_path_info = null;
        ClassLoader old_classloader = null;
        Thread current_thread = null;
        String pathInfo = target;

		DispatcherType dispatch = baseRequest.getDispatcherType();// DispatcherType.REQUEST

		old_context = baseRequest.getContext();// 空--获取原来的请求上下文环境

		if (old_context != _scontext) {// 当不是当前的上下文环境
            
            if (DispatcherType.REQUEST.equals(dispatch) ||
                DispatcherType.ASYNC.equals(dispatch) ||
                DispatcherType.ERROR.equals(dispatch) && baseRequest.getHttpChannelState().isAsync()) {// check the target.

				if (_compactPath) {// 处理路径()
					target = URIUtil.compactPath(target);
				}
				if (!checkContext(target, baseRequest, response)) {// ????
					return;
				}

				if (target.length() > _contextPath.length()) {
					if (_contextPath.length() > 1) {
						target = target.substring(_contextPath.length());
					}
                    pathInfo = target;
				} else if (_contextPath.length() == 1) {
                    target = URIUtil.SLASH;
                    pathInfo = URIUtil.SLASH;
				} else {
                    target = URIUtil.SLASH;
                    pathInfo = null;
                }
            }

			if (_classLoader != null) {
                current_thread = Thread.currentThread();
                old_classloader = current_thread.getContextClassLoader();
                current_thread.setContextClassLoader(_classLoader);
            }
        }

		try {

            old_context_path = baseRequest.getContextPath();
            old_servlet_path = baseRequest.getServletPath();
            old_path_info = baseRequest.getPathInfo();

			baseRequest.setContext(_scontext);

			__context.set(_scontext);

			if (!DispatcherType.INCLUDE.equals(dispatch) && target.startsWith("/")) {
				if (_contextPath.length() == 1) {
					baseRequest.setContextPath("");
				} else {
					baseRequest.setContextPath(_contextPath);
				}
                baseRequest.setServletPath(null);
                baseRequest.setPathInfo(pathInfo);
            }

			if (old_context != _scontext) {
				enterScope(baseRequest, dispatch);
			}

			if (never()) {
				nextScope(target, baseRequest, request, response);
			} else if (_nextScope != null) {
				_nextScope.doScope(target, baseRequest, request, response);// ServletHandler
			} else if (_outerScope != null) {
				_outerScope.doHandle(target, baseRequest, request, response);
			} else {
				doHandle(target, baseRequest, request, response);// 下面
			}
		} finally {

			if (old_context != _scontext) {

                exitScope(baseRequest);

				if (_classLoader != null && current_thread != null) {
                    current_thread.setContextClassLoader(old_classloader);
                }

                baseRequest.setContext(old_context);

                __context.set(old_context);

                baseRequest.setContextPath(old_context_path);
                baseRequest.setServletPath(old_servlet_path);
                baseRequest.setPathInfo(old_path_info);
            }
        }
    }
    @Override
	public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		System.out.println("请求处理过程: 7. ContextHandler");

        final DispatcherType dispatch = baseRequest.getDispatcherType();

        final boolean new_context = baseRequest.takeNewContext();

		try {
			if (new_context) {
				if (!_servletRequestAttributeListeners.isEmpty()) {
					for (ServletRequestAttributeListener l : _servletRequestAttributeListeners) {
						baseRequest.addEventListener(l);
					}
				}
				if (!_servletRequestListeners.isEmpty()) {
                    final ServletRequestEvent sre = new ServletRequestEvent(_scontext,request);
					for (ServletRequestListener l : _servletRequestListeners) {
						l.requestInitialized(sre);
					}
                }
            }
			if (DispatcherType.REQUEST.equals(dispatch) && isProtectedTarget(target)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                baseRequest.setHandled(true);
                return;
            }
			if (never()) {
                nextHandle(target,baseRequest,request,response);
			} else if (_nextScope != null && _nextScope == _handler) {
				_nextScope.doHandle(target, baseRequest, request, response);// ServletHandler
			} else if (_handler != null) {
				_handler.handle(target, baseRequest, request, response);// 请求路径对应的HANDLER
			}
		} finally {
			if (new_context) {
				if (!_servletRequestListeners.isEmpty()) {
                    final ServletRequestEvent sre = new ServletRequestEvent(_scontext,request);
					for (int i = _servletRequestListeners.size(); i-- > 0;) {
						_servletRequestListeners.get(i).requestDestroyed(sre);
					}
                }

				if (!_servletRequestAttributeListeners.isEmpty()) {
					for (int i = _servletRequestAttributeListeners.size(); i-- > 0;) {
						baseRequest.removeEventListener(_servletRequestAttributeListeners.get(i));
					}
                }
            }
        }
    }
	// **********************************************************************************************************

	protected void enterScope(Request request, Object reason) {
		if (!_contextListeners.isEmpty()) {
			for (ContextScopeListener listener : _contextListeners) {
				try {
                    listener.enterScope(_scontext,request,reason);
				} catch (Throwable e) {
                    LOG.warn(e);
                }
            }
        }
    }
	protected void exitScope(Request request) {
		if (!_contextListeners.isEmpty()) {
			for (int i = _contextListeners.size(); i-- > 0;) {
				try {
                    _contextListeners.get(i).exitScope(_scontext,request);
				} catch (Throwable e) {
                    LOG.warn(e);
                }
            }
        }
    }
	public void handle(Request request, Runnable runnable) {
        ClassLoader old_classloader = null;
        Thread current_thread = null;
        Context old_context = __context.get();

		if (old_context == _scontext) {
            runnable.run();
            return;
        }

		try {
            __context.set(_scontext);

			if (_classLoader != null) {
                current_thread = Thread.currentThread();
                old_classloader = current_thread.getContextClassLoader();
                current_thread.setContextClassLoader(_classLoader);
            }

            enterScope(request,runnable);
            runnable.run();
		} finally {
            exitScope(request);

            __context.set(old_context);
			if (old_classloader != null) {
                current_thread.setContextClassLoader(old_classloader);
            }
        }
    }

	public void handle(Runnable runnable) {
        handle(null,runnable);
    }
	public boolean isProtectedTarget(String target) {
		if (target == null || _protectedTargets == null) {
			return false;
		}

		while (target.startsWith("//")) {
			target = URIUtil.compactPath(target);
		}

		for (int i = 0; i < _protectedTargets.length; i++) {
            String t=_protectedTargets[i];
			if (StringUtil.startsWithIgnoreCase(target, t)) {
				if (target.length() == t.length()) {
					return true;
				}
                char c=target.charAt(t.length());
				if (c == '/' || c == '?' || c == '#' || c == ';') {
					return true;
				}
            }
        }
        return false;
    }
	public void setProtectedTargets(String[] targets) {
		if (targets == null) {
            _protectedTargets = null;
            return;
        }
        _protectedTargets = Arrays.copyOf(targets, targets.length);
    }
	public String[] getProtectedTargets() {
		if (_protectedTargets == null) {
			return null;
		}
        return Arrays.copyOf(_protectedTargets, _protectedTargets.length);
    }

    @Override
	public void removeAttribute(String name) {
        _attributes.removeAttribute(name);
    }
    @Override
	public void setAttribute(String name, Object value) {
        _attributes.setAttribute(name,value);
    }
	public void setAttributes(Attributes attributes) {
        _attributes.clearAttributes();
        _attributes.addAll(attributes);
    }
    @Override
	public void clearAttributes() {
        _attributes.clearAttributes();
    }

	public void setManagedAttribute(String name, Object value) {
		Object old = _managedAttributes.put(name, value);
		updateBean(old, value);
    }
	public void setClassLoader(ClassLoader classLoader) {
        _classLoader = classLoader;
    }

	public void setContextPath(String contextPath) {// 设置上下文的路径(根处理器的路径)

		if (contextPath == null) {
			throw new IllegalArgumentException("null contextpath");
		}

		if (contextPath.endsWith("/*")) {
			LOG.warn(this + " contextpath ends with /*");
			contextPath = contextPath.substring(0, contextPath.length() - 2);
		} else if (contextPath.length() > 1 && contextPath.endsWith("/")) {
			LOG.warn(this + " contextpath ends with /");
			contextPath = contextPath.substring(0, contextPath.length() - 1);
        }

		if (contextPath.length() == 0) {
			LOG.warn("empty contextpath");
			contextPath = "/";
        }

		_contextPath = contextPath;//

		if (getServer() != null && (getServer().isStarting() || getServer().isStarted())) {// 表示服务器已经启动

			Handler[] contextCollections = getServer().getChildHandlersByClass(ContextHandlerCollection.class);

			for (int h = 0; contextCollections != null && h < contextCollections.length; h++) {
                ((ContextHandlerCollection)contextCollections[h]).mapContexts();
			}
        }
    }
	public void setDisplayName(String servletContextName) {
        _displayName = servletContextName;
    }

	public Resource getBaseResource() {
		if (_baseResource == null) {
            return null;
		}
        return _baseResource;
    }
    @ManagedAttribute("document root for context")
	public String getResourceBase() {
		if (_baseResource == null) {
            return null;
		}
        return _baseResource.toString();
    }

	public void setBaseResource(Resource base) {
        _baseResource = base;
    }

	public void setResourceBase(String resourceBase) {
		try {
            setBaseResource(newResource(resourceBase));
		} catch (Exception e) {
            LOG.warn(e.toString());
            LOG.debug(e);
            throw new IllegalArgumentException(resourceBase);
        }
    }

	public MimeTypes getMimeTypes() {
		if (_mimeTypes == null) {
            _mimeTypes = new MimeTypes();
		}
        return _mimeTypes;
    }

	public void setMimeTypes(MimeTypes mimeTypes) {
        _mimeTypes = mimeTypes;
    }

	public void setWelcomeFiles(String[] files) {
        _welcomeFiles = files;
    }
	public String[] getWelcomeFiles() {
        return _welcomeFiles;
    }
	public ErrorHandler getErrorHandler() {
        return _errorHandler;
    }
	public void setErrorHandler(ErrorHandler errorHandler) {
		if (errorHandler != null) {
            errorHandler.setServer(getServer());
		}
		updateBean(_errorHandler, errorHandler, true);
        _errorHandler = errorHandler;
    }
	public int getMaxFormContentSize() {
        return _maxFormContentSize;
    }

	public void setMaxFormContentSize(int maxSize) {
        _maxFormContentSize = maxSize;
    }

	public int getMaxFormKeys() {
        return _maxFormKeys;
    }

	public void setMaxFormKeys(int max) {
        _maxFormKeys = max;
    }

	public boolean isCompactPath() {
        return _compactPath;
    }

	public void setCompactPath(boolean compactPath) {
        _compactPath = compactPath;
    }
    @Override
	public String toString() {
        String[] vhosts = getVirtualHosts();
        StringBuilder b = new StringBuilder();
        Package pkg = getClass().getPackage();
		if (pkg != null) {
            String p = pkg.getName();
			if (p != null && p.length() > 0) {
                String[] ss = p.split("\\.");
				for (String s : ss) {
                    b.append(s.charAt(0)).append('.');
				}
            }
        }
        b.append(getClass().getSimpleName()).append('@').append(Integer.toString(hashCode(),16));
        b.append('{').append(getContextPath()).append(',').append(getBaseResource()).append(',').append(_availability);
		if (vhosts != null && vhosts.length > 0) {
            b.append(',').append(vhosts[0]);
		}
        b.append('}');

        return b.toString();
    }

	public synchronized Class<?> loadClass(String className) throws ClassNotFoundException {
		if (className == null) {
            return null;
		}
		if (_classLoader == null) {
            return Loader.loadClass(this.getClass(),className);
		}
        return _classLoader.loadClass(className);
    }

	public void addLocaleEncoding(String locale, String encoding) {
		if (_localeEncodingMap == null) {
            _localeEncodingMap = new HashMap<String, String>();
		}
        _localeEncodingMap.put(locale,encoding);
    }

	public String getLocaleEncoding(String locale) {
		if (_localeEncodingMap == null) {
            return null;
		}
        String encoding = _localeEncodingMap.get(locale);
        return encoding;
    }

	public String getLocaleEncoding(Locale locale) {
		if (_localeEncodingMap == null) {
            return null;
		}
        String encoding = _localeEncodingMap.get(locale.toString());
		if (encoding == null) {
            encoding = _localeEncodingMap.get(locale.getLanguage());
		}
        return encoding;
    }

	public Map<String, String> getLocaleEncodings() {
		if (_localeEncodingMap == null) {
            return null;
		}
        return Collections.unmodifiableMap(_localeEncodingMap);
    }

	public Resource getResource(String path) throws MalformedURLException {
		if (path == null || !path.startsWith(URIUtil.SLASH)) {
            throw new MalformedURLException(path);
		}
		if (_baseResource == null) {
            return null;
		}
		try {
            path = URIUtil.canonicalPath(path);
            Resource resource = _baseResource.addPath(path);

			if (checkAlias(path, resource)) {
                return resource;
			}
            return null;
		} catch (Exception e) {
            LOG.ignore(e);
        }

        return null;
    }

	public boolean checkAlias(String path, Resource resource) {
        // Is the resource aliased?
		if (resource.isAlias()) {
			if (LOG.isDebugEnabled()) {
                LOG.debug("Aliased resource: " + resource + "~=" + resource.getAlias());
			}
            // alias checks
			for (Iterator<AliasCheck> i = _aliasChecks.iterator(); i.hasNext();) {
                AliasCheck check = i.next();
				if (check.check(path, resource)) {
					if (LOG.isDebugEnabled()) {
                        LOG.debug("Aliased resource: " + resource + " approved by " + check);
					}
                    return true;
                }
            }
            return false;
        }
        return true;
    }

	public Resource newResource(URL url) throws IOException {
        return Resource.newResource(url);
    }

	public Resource newResource(URI uri) throws IOException {
        return Resource.newResource(uri);
    }

	public Resource newResource(String urlOrPath) throws IOException {
        return Resource.newResource(urlOrPath);
    }

	public Set<String> getResourcePaths(String path) {
		try {
            path = URIUtil.canonicalPath(path);
            Resource resource = getResource(path);

			if (resource != null && resource.exists()) {
				if (!path.endsWith(URIUtil.SLASH)) {
                    path = path + URIUtil.SLASH;
				}
                String[] l = resource.list();
				if (l != null) {
                    HashSet<String> set = new HashSet<String>();
					for (int i = 0; i < l.length; i++) {
                        set.add(path + l[i]);
					}
                    return set;
                }
            }
		} catch (Exception e) {
            LOG.ignore(e);
        }
        return Collections.emptySet();
    }

	private String normalizeHostname(String host) {// 将主机名称标准化--去掉最后一个点
		if (host == null) {
            return null;
		}
		if (host.endsWith(".")) {
			return host.substring(0, host.length() - 1);
		}
        return host;
    }

	public void addAliasCheck(AliasCheck check) {
        _aliasChecks.add(check);
    }

	public List<AliasCheck> getAliasChecks() {
        return _aliasChecks;
    }

	public void setAliasChecks(List<AliasCheck> checks) {
        _aliasChecks.clear();
        _aliasChecks.addAll(checks);
    }

	public void clearAliasChecks() {
        _aliasChecks.clear();
    }

	public class Context extends StaticContext {// 上下文实现类(基类)

		protected boolean _enabled = true;
        protected boolean _extendedListenerTypes = false;

		// ******************************************************
		protected Context() {

        }
		// ******************************************************

		public ContextHandler getContextHandler() {// 获取所在的处理器
			return ContextHandler.this;
        }
        @Override
		public ServletContext getContext(String uripath) {// 表示根据URL路径获取SERVLET环境

			List<ContextHandler> contexts = new ArrayList<ContextHandler>();

			Handler[] handlers = getServer().getChildHandlersByClass(ContextHandler.class);//

            String matched_path = null;

			for (Handler handler : handlers) {//

				if (handler == null) {
                    continue;
				}

                ContextHandler ch = (ContextHandler)handler;

                String context_path = ch.getContextPath();

				if (uripath.equals(context_path) || (uripath.startsWith(context_path) && uripath.charAt(context_path.length()) == '/') || "/".equals(context_path)) {

					if (getVirtualHosts() != null && getVirtualHosts().length > 0) {// 虚拟主机
						if (ch.getVirtualHosts() != null && ch.getVirtualHosts().length > 0) {
							for (String h1 : getVirtualHosts()) {
								for (String h2 : ch.getVirtualHosts()) {
									if (h1.equals(h2)) {
										if (matched_path == null || context_path.length() > matched_path.length()) {
                                            contexts.clear();
                                            matched_path = context_path;
                                        }
										if (matched_path.equals(context_path)) {
                                            contexts.add(ch);
										}
                                    }
								}
							}
                        }
					} else {
						if (matched_path == null || context_path.length() > matched_path.length()) {
                            contexts.clear();
                            matched_path = context_path;
                        }
						if (matched_path.equals(context_path)) {
                            contexts.add(ch);
						}
                    }
                }
            }
			if (contexts.size() > 0) {
				return contexts.get(0)._scontext;//
			}
			matched_path = null;
			for (Handler handler : handlers) {
				if (handler == null) {
                    continue;
				}
				ContextHandler ch = (ContextHandler) handler;
                String context_path = ch.getContextPath();
				if (uripath.equals(context_path) || (uripath.startsWith(context_path) && uripath.charAt(context_path.length()) == '/') || "/".equals(context_path)) {
					if (matched_path == null || context_path.length() > matched_path.length()) {
                        contexts.clear();
                        matched_path = context_path;
                    }
					if (matched_path != null && matched_path.equals(context_path)) {
                        contexts.add(ch);
					}
                }
            }
			if (contexts.size() > 0) {
				return contexts.get(0)._scontext;
			}
            return null;
        }
        @Override
		public String getMimeType(String file) {
			if (_mimeTypes == null) {
                return null;
			}
            return _mimeTypes.getMimeByExtension(file);
        }
        @Override
		public RequestDispatcher getRequestDispatcher(String uriInContext) {
			if (uriInContext == null) {
                return null;
			}
			if (!uriInContext.startsWith("/")) {
                return null;
			}
			try {
                HttpURI uri = new HttpURI(null,null,0,uriInContext);

                String pathInfo=URIUtil.canonicalPath(uri.getDecodedPath());
				if (pathInfo == null) {
                    return null;
				}
                String contextPath=getContextPath();
				if (contextPath != null && contextPath.length() > 0) {
                    uri.setPath(URIUtil.addPaths(contextPath,uri.getPath()));
				}
                return new Dispatcher(ContextHandler.this,uri,pathInfo);
			} catch (Exception e) {
                LOG.ignore(e);
            }
            return null;
        }
        @Override
		public String getRealPath(String path) {
			if (path == null) {
                return null;
			}
			if (path.length() == 0) {
                path = URIUtil.SLASH;
			} else if (path.charAt(0) != '/') {
                path = URIUtil.SLASH + path;
			}
			try {
                Resource resource = ContextHandler.this.getResource(path);
				if (resource != null) {
                    File file = resource.getFile();
					if (file != null) {
                        return file.getCanonicalPath();
					}
                }
			} catch (Exception e) {
                LOG.ignore(e);
            }
            return null;
        }
        @Override
		public URL getResource(String path) throws MalformedURLException {
            Resource resource = ContextHandler.this.getResource(path);
			if (resource != null && resource.exists()) {
                return resource.getURI().toURL();
			}
            return null;
        }
        @Override
		public InputStream getResourceAsStream(String path) {
			try {
                URL url = getResource(path);
				if (url == null) {
                    return null;
				}
                Resource r = Resource.newResource(url);
                return r.getInputStream();
			} catch (Exception e) {
                LOG.ignore(e);
                return null;
            }
        }
        @Override
		public Set<String> getResourcePaths(String path) {
            return ContextHandler.this.getResourcePaths(path);
        }
        @Override
		public void log(Exception exception, String msg) {
            _logger.warn(msg,exception);
        }

		@Override
		public void log(String msg) {
            _logger.info(msg);
        }
        @Override
		public void log(String message, Throwable throwable) {
            _logger.warn(message,throwable);
        }
        @Override
		public String getInitParameter(String name) {
            return ContextHandler.this.getInitParameter(name);
        }
        @Override
		public Enumeration<String> getInitParameterNames() {
            return ContextHandler.this.getInitParameterNames();
        }
        @Override
		public synchronized Object getAttribute(String name) {
            Object o = ContextHandler.this.getAttribute(name);
			if (o == null) {
                o = super.getAttribute(name);
			}
            return o;
        }
        @Override
		public synchronized Enumeration<String> getAttributeNames() {
            HashSet<String> set = new HashSet<String>();
            Enumeration<String> e = super.getAttributeNames();
			while (e.hasMoreElements()) {
                set.add(e.nextElement());
			}
            e = _attributes.getAttributeNames();
			while (e.hasMoreElements()) {
                set.add(e.nextElement());
			}
            return Collections.enumeration(set);
        }
        @Override
		public synchronized void setAttribute(String name, Object value) {//
           
        	Object old_value = super.getAttribute(name);
            
            if (value == null){
                super.removeAttribute(name);
			} else {
                super.setAttribute(name,value);
			}
            
			if (!_servletContextAttributeListeners.isEmpty()) {
               
				ServletContextAttributeEvent event = new ServletContextAttributeEvent(_scontext, name, old_value == null ? value : old_value);
				
                for (ServletContextAttributeListener l : _servletContextAttributeListeners) {
					if (old_value == null) {
                        l.attributeAdded(event);
					} else if (value == null) {
                        l.attributeRemoved(event);
					} else {
                        l.attributeReplaced(event);
					}
                }
            }
        }
        @Override
		public synchronized void removeAttribute(String name) {
            Object old_value = super.getAttribute(name);
            super.removeAttribute(name);
			if (old_value != null && !_servletContextAttributeListeners.isEmpty()) {
                ServletContextAttributeEvent event = new ServletContextAttributeEvent(_scontext,name,old_value);
				for (ServletContextAttributeListener l : _servletContextAttributeListeners) {
                    l.attributeRemoved(event);
				}
            }
        }
        @Override
		public String getServletContextName() {
            String name = ContextHandler.this.getDisplayName();
			if (name == null) {
                name = ContextHandler.this.getContextPath();
			}
            return name;
        }
        @Override
		public String getContextPath() {
			if ((_contextPath != null) && _contextPath.equals(URIUtil.SLASH)) {
                return "";
			}
            return _contextPath;
        }

        @Override
		public String toString() {
            return "ServletContext@" + ContextHandler.this.toString();
        }

        @Override
		public boolean setInitParameter(String name, String value) {
			if (ContextHandler.this.getInitParameter(name) != null) {
                return false;
			}
            ContextHandler.this.getInitParams().put(name,value);
            return true;
        }

        @Override
		public void addListener(String className) {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
			try {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Class<? extends EventListener> clazz = _classLoader==null?Loader.loadClass(ContextHandler.class,className):(Class)_classLoader.loadClass(className);
                addListener(clazz);
			} catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
		public <T extends EventListener> void addListener(T t) {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            checkListener(t.getClass());

            ContextHandler.this.addEventListener(t);
            ContextHandler.this.addProgrammaticListener(t);
        }

        @Override
		public void addListener(Class<? extends EventListener> listenerClass) {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
			try {
                EventListener e = createListener(listenerClass);
                addListener(e);
			} catch (ServletException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
		public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
			try {
                return createInstance(clazz);
			} catch (Exception e) {
                throw new ServletException(e);
            }
        }


		public void checkListener(Class<? extends EventListener> listener) throws IllegalStateException {
            boolean ok = false;
            int startIndex = (isExtendedListenerTypes()?EXTENDED_LISTENER_TYPE_INDEX:DEFAULT_LISTENER_TYPE_INDEX);
			for (int i = startIndex; i < SERVLET_LISTENER_TYPES.length; i++) {
				if (SERVLET_LISTENER_TYPES[i].isAssignableFrom(listener)) {
                    ok = true;
                    break;
                }
            }
			if (!ok) {
                throw new IllegalArgumentException("Inappropriate listener class "+listener.getName());
			}
        }

		public void setExtendedListenerTypes(boolean extended) {
            _extendedListenerTypes = extended;
        }

		public boolean isExtendedListenerTypes() {
            return _extendedListenerTypes;
        }

        @Override
		public ClassLoader getClassLoader() {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
			if (!_usingSecurityManager) {
                return _classLoader;
			} else {
				try {
                    Class<?> reflect = Loader.loadClass(getClass(), "sun.reflect.Reflection");
                    Method getCallerClass = reflect.getMethod("getCallerClass", Integer.TYPE);
                    Class<?> caller = (Class<?>)getCallerClass.invoke(null, 2);
                    boolean ok = false;
                    ClassLoader callerLoader = caller.getClassLoader();
					while (!ok && callerLoader != null) {
						if (callerLoader == _classLoader) {
                            ok = true;
						} else {
                            callerLoader = callerLoader.getParent();
						}
                    }
					if (ok) {
                        return _classLoader;
					}
				} catch (Exception e) {
                    LOG.warn("Unable to check classloader of caller",e);
                }
                AccessController.checkPermission(new RuntimePermission("getClassLoader"));
                return _classLoader;
            }
        }
        @Override
		public JspConfigDescriptor getJspConfigDescriptor() {
            LOG.warn(__unimplmented);
            return null;
        }
		public void setJspConfigDescriptor(JspConfigDescriptor d) {

        }
        @Override
		public void declareRoles(String... roleNames) {
			if (!isStarting()) {
                throw new IllegalStateException ();
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
        }
		public void setEnabled(boolean enabled) {
            _enabled = enabled;
        }
		public boolean isEnabled() {
            return _enabled;
        }
		public <T> T createInstance(Class<T> clazz) throws Exception {
            T o = clazz.newInstance();
            return o;
        }
        @Override
		public String getVirtualServerName() {
            String[] hosts = getVirtualHosts();
			if (hosts != null && hosts.length > 0) {
                return hosts[0];
			}
            return null;
        }
    }

	public static class StaticContext extends AttributesMap implements ServletContext {//

        private int _effectiveMajorVersion = SERVLET_MAJOR_VERSION;
        private int _effectiveMinorVersion = SERVLET_MINOR_VERSION;

		public StaticContext() {
        }
        @Override
		public ServletContext getContext(String uripath) {
            return null;
        }
        @Override
		public int getMajorVersion() {
            return SERVLET_MAJOR_VERSION;
        }
        @Override
		public String getMimeType(String file) {
            return null;
        }
        @Override
		public int getMinorVersion() {
            return SERVLET_MINOR_VERSION;
        }
        @Override
		public RequestDispatcher getNamedDispatcher(String name) {
            return null;
        }
        @Override
		public RequestDispatcher getRequestDispatcher(String uriInContext) {
            return null;
        }
        @Override
		public String getRealPath(String path) {
            return null;
        }
        @Override
		public URL getResource(String path) throws MalformedURLException {
            return null;
        }
        @Override
		public InputStream getResourceAsStream(String path) {
            return null;
        }
        @Override
		public Set<String> getResourcePaths(String path) {
            return null;
        }
        @Override
		public String getServerInfo() {
            return __serverInfo;
        }
        @Override
        @Deprecated
		public Servlet getServlet(String name) throws ServletException {
            return null;
        }
        @SuppressWarnings("unchecked")
        @Override
        @Deprecated
		public Enumeration<String> getServletNames() {
            return Collections.enumeration(Collections.EMPTY_LIST);
        }
        @SuppressWarnings("unchecked")
        @Override
        @Deprecated
		public Enumeration<Servlet> getServlets() {
            return Collections.enumeration(Collections.EMPTY_LIST);
        }
        @Override
		public void log(Exception exception, String msg) {
            LOG.warn(msg,exception);
        }
        @Override
		public void log(String msg) {
            LOG.info(msg);
        }
        @Override
		public void log(String message, Throwable throwable) {
            LOG.warn(message,throwable);
        }
        @Override
		public String getInitParameter(String name) {
            return null;
        }
        @SuppressWarnings("unchecked")
        @Override
		public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(Collections.EMPTY_LIST);
        }
		@Override
		public String getServletContextName() {
            return "No Context";
        }
        @Override
		public String getContextPath() {
            return null;
        }
        @Override
		public boolean setInitParameter(String name, String value) {
            return false;
        }
        @Override
		public Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public Dynamic addFilter(String filterName, Filter filter) {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public Dynamic addFilter(String filterName, String className) {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName, String className) {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public FilterRegistration getFilterRegistration(String filterName) {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public ServletRegistration getServletRegistration(String servletName) {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public Map<String, ? extends ServletRegistration> getServletRegistrations() {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public SessionCookieConfig getSessionCookieConfig() {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
            LOG.warn(__unimplmented);
        }
        @Override
		public void addListener(String className) {
            LOG.warn(__unimplmented);
        }
        @Override
		public <T extends EventListener> void addListener(T t) {
            LOG.warn(__unimplmented);
        }
        @Override
		public void addListener(Class<? extends EventListener> listenerClass) {
            LOG.warn(__unimplmented);
        }
        @Override
		public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
			try {
                return clazz.newInstance();
			} catch (InstantiationException e) {
                throw new ServletException(e);
			} catch (IllegalAccessException e) {
                throw new ServletException(e);
            }
        }
        @Override
		public ClassLoader getClassLoader() {
            return ContextHandler.class.getClassLoader();
        }
        @Override
		public int getEffectiveMajorVersion() {
            return _effectiveMajorVersion;
        }
        @Override
		public int getEffectiveMinorVersion() {
            return _effectiveMinorVersion;
        }
		public void setEffectiveMajorVersion(int v) {
            _effectiveMajorVersion = v;
        }
		public void setEffectiveMinorVersion(int v) {
            _effectiveMinorVersion = v;
        }
        @Override
		public JspConfigDescriptor getJspConfigDescriptor() {
            LOG.warn(__unimplmented);
            return null;
        }
        @Override
		public void declareRoles(String... roleNames) {
            LOG.warn(__unimplmented);
        }
        @Override
		public String getVirtualServerName() {
            return null;
        }
    }
	public interface AliasCheck {
        boolean check(String path, Resource resource);
    }
	public static class ApproveAliases implements AliasCheck {
        @Override
		public boolean check(String path, Resource resource) {
            return true;
        }
    }
	public static class ApproveNonExistentDirectoryAliases implements AliasCheck {
        @Override
		public boolean check(String path, Resource resource) {
			if (resource.exists()) {
                return false;
			}
			String a = resource.getAlias().toString();
			String r = resource.getURI().toString();
			if (a.length() > r.length()) {
				return a.startsWith(r) && a.length() == r.length() + 1 && a.endsWith("/");
			}
			if (a.length() < r.length()) {
				return r.startsWith(a) && r.length() == a.length() + 1 && r.endsWith("/");
			}
            return a.equals(r);
        }
    }
	public static interface ContextScopeListener extends EventListener {
        void enterScope(Context context, Request request, Object reason);
        void exitScope(Context context, Request request);
    }
}
