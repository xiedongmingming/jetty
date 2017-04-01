package org.eclipse.jetty.servlet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletSecurityElement;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;
import javax.servlet.descriptor.TaglibDescriptor;

import org.eclipse.jetty.security.ConstraintAware;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.BaseHolder.Source;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.DeprecationWarning;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ServletContextHandler extends ContextHandler {// 根处理器(表示主机名对应的根处理器)
	
	// 通常和默认处理器并存

	// 用于SERVLET的处理--所有的SERVELT都共用同一个CONTEXT

	// 将SERVLET引入到HTTP的请求处理中

    private static final Logger LOG = Log.getLogger(ServletContextHandler.class);
    
	// 对应与选项字段
	public final static int SESSIONS = 1;
	public final static int SECURITY = 2;
	public final static int GZIP = 4;
	public final static int NO_SESSIONS = 0;
	public final static int NO_SECURITY = 0;
    
    public interface ServletContainerInitializerCaller extends LifeCycle {};

	protected final DecoratedObjectFactory _objFactory;//

	protected Class<? extends SecurityHandler> _defaultSecurityHandlerClass = org.eclipse.jetty.security.ConstraintSecurityHandler.class;

	protected SessionHandler _sessionHandler;
    protected SecurityHandler _securityHandler;
	protected ServletHandler _servletHandler;// 保存所有的SERVLET(用于处理)
    protected GzipHandler _gzipHandler;

	protected int _options;
    protected JspConfigDescriptor _jspConfig;

	// **********************************************************************************************************
    public ServletContextHandler() {
		this(null, null, null, null, null);
    }
    public ServletContextHandler(int options) {
		this(null, null, options);
    }
	public ServletContextHandler(HandlerContainer parent, String contextPath) {
		this(parent, contextPath, null, null, null, null);
    }
    public ServletContextHandler(HandlerContainer parent, String contextPath, int options) {
		this(parent, contextPath, null, null, null, null, options);
    }
    public ServletContextHandler(HandlerContainer parent, String contextPath, boolean sessions, boolean security) {
		this(parent, contextPath, (sessions ? SESSIONS : 0) | (security ? SECURITY : 0));
    }
    public ServletContextHandler(HandlerContainer parent, SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler) {
		this(parent, null, sessionHandler, securityHandler, servletHandler, errorHandler);
    }
    public ServletContextHandler(HandlerContainer parent, String contextPath, SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler) {
		this(parent, contextPath, sessionHandler, securityHandler, servletHandler, errorHandler, 0);
    }
	public ServletContextHandler(HandlerContainer parent, // 默认为空--表示该处理器的父容器
			String contextPath, // 默认为空--对应的路径
			SessionHandler sessionHandler, // 默认为空--会话处理器
			SecurityHandler securityHandler, // 默认为空--安全相关的处理器
			ServletHandler servletHandler, // 默认为空--用于处理SERVELT
			ErrorHandler errorHandler, // 默认为空--用于处理错误
			int options) {// 默认为0

		// 共有7个构造函数

		super((ContextHandler.Context) null);//

		_options = options;//

		_scontext = new Context();// 内部类(对应SERVLET的CONTEXT)

        _sessionHandler = sessionHandler;
        _securityHandler = securityHandler;
        _servletHandler = servletHandler;

        _objFactory = new DecoratedObjectFactory();
		_objFactory.addDecorator(new DeprecationWarning());// 添加装饰器

		if (contextPath != null) {
            setContextPath(contextPath);
		}

		if (parent instanceof HandlerWrapper) {
			((HandlerWrapper) parent).setHandler(this);
		} else if (parent instanceof HandlerCollection) {
			((HandlerCollection) parent).addHandler(this);
		}

		relinkHandlers();

		if (errorHandler != null) {
            setErrorHandler(errorHandler);
		}
    }
	// **********************************************************************************************************
	// 接口函数实现: HandlerWrapper
    @Override
	public void setHandler(Handler handler) {
		if (handler != null) {
            LOG.warn("ServletContextHandler.setHandler should not be called directly. Use insertHandler or setSessionHandler etc.");
		}
		super.setHandler(handler);
    }

	// **********************************************************************************************************
	private void doSetHandler(HandlerWrapper wrapper, Handler handler) {
		if (wrapper == this) {
            super.setHandler(handler);
		} else {
            wrapper.setHandler(handler);
		}
    }
	private void relinkHandlers() {
		
		HandlerWrapper handler = this;
		
		if (getSessionHandler() != null) {

			while (!(handler.getHandler() instanceof SessionHandler) && !(handler.getHandler() instanceof SecurityHandler) && !(handler.getHandler() instanceof GzipHandler) && !(handler.getHandler() instanceof ServletHandler) && handler.getHandler() instanceof HandlerWrapper) {
				handler = (HandlerWrapper) handler.getHandler();
			}

			if (handler.getHandler() != _sessionHandler) {
				doSetHandler(handler, _sessionHandler);
			}

			handler = _sessionHandler;
        }
		if (getSecurityHandler() != null) {

			while (!(handler.getHandler() instanceof SecurityHandler) && !(handler.getHandler() instanceof GzipHandler) && !(handler.getHandler() instanceof ServletHandler) && handler.getHandler() instanceof HandlerWrapper) {
				handler = (HandlerWrapper) handler.getHandler();
			}

			if (handler.getHandler() != _securityHandler) {
				doSetHandler(handler, _securityHandler);
			}

			handler = _securityHandler;
        }
		if (getGzipHandler() != null) {

			while (!(handler.getHandler() instanceof GzipHandler) && !(handler.getHandler() instanceof ServletHandler) && handler.getHandler() instanceof HandlerWrapper) {
				handler = (HandlerWrapper) handler.getHandler();
			}

			if (handler.getHandler() != _gzipHandler) {
				doSetHandler(handler, _gzipHandler);
			}

			handler = _gzipHandler;
        }
		if (getServletHandler() != null) {

			while (!(handler.getHandler() instanceof ServletHandler) && handler.getHandler() instanceof HandlerWrapper) {
				handler = (HandlerWrapper) handler.getHandler();
			}

			if (handler.getHandler() != _servletHandler) {
				doSetHandler(handler, _servletHandler);
			}

			handler = _servletHandler;
        }
    }
    @Override
	protected void doStart() throws Exception {
        getServletContext().setAttribute(DecoratedObjectFactory.ATTR, _objFactory);
        super.doStart();
    }
    @Override
	protected void doStop() throws Exception {
        super.doStop();
        _objFactory.clear();
    }
	public Class<? extends SecurityHandler> getDefaultSecurityHandlerClass() {
        return _defaultSecurityHandlerClass;
    }
	public void setDefaultSecurityHandlerClass(Class<? extends SecurityHandler> defaultSecurityHandlerClass) {
        _defaultSecurityHandlerClass = defaultSecurityHandlerClass;
    }
	protected SessionHandler newSessionHandler() {
        return new SessionHandler();
    }
	protected SecurityHandler newSecurityHandler() {
		try {
            return _defaultSecurityHandlerClass.newInstance();
		} catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
	protected ServletHandler newServletHandler() {
        return new ServletHandler();
    }
    @Override
	protected void startContext() throws Exception {
        ServletContainerInitializerCaller sciBean = getBean(ServletContainerInitializerCaller.class);
		if (sciBean != null) {
            sciBean.start();
		}
		if (_servletHandler != null) {
			if (_servletHandler.getListeners() != null) {
				for (ListenerHolder holder : _servletHandler.getListeners()) {
                    _objFactory.decorate(holder.getListener());
                }
            }
        }
		super.startContext();
		if (_servletHandler != null) {
            _servletHandler.initialize();
		}
    }
    @Override
	protected void stopContext() throws Exception {
        super.stopContext();
    }
	public SecurityHandler getSecurityHandler() {
		if (_securityHandler == null && (_options & SECURITY) != 0 && !isStarted()) {
			_securityHandler = newSecurityHandler();
		}
        return _securityHandler;
    }
	public ServletHandler getServletHandler() {
		if (_servletHandler == null && !isStarted()) {
			_servletHandler = newServletHandler();
		}
        return _servletHandler;
    }
	public SessionHandler getSessionHandler() {
		if (_sessionHandler == null && (_options & SESSIONS) != 0 && !isStarted()) {
			_sessionHandler = newSessionHandler();
		}
        return _sessionHandler;
    }
	public GzipHandler getGzipHandler() {
		if (_gzipHandler == null && (_options & GZIP) != 0 && !isStarted()) {
			_gzipHandler = new GzipHandler();
		}
        return _gzipHandler;
    }

	// **********************************************************************************************
	// 添加SERVELT
	public ServletHolder addServlet(String className, String pathSpec) {
        return getServletHandler().addServletWithMapping(className, pathSpec);
    }
	public ServletHolder addServlet(Class<? extends Servlet> servlet, String pathSpec) {
        return getServletHandler().addServletWithMapping(servlet.getName(), pathSpec);
    }
	public void addServlet(ServletHolder servlet, String pathSpec) {
        getServletHandler().addServletWithMapping(servlet, pathSpec);
    }
	public void addFilter(FilterHolder holder, String pathSpec, EnumSet<DispatcherType> dispatches) {
		getServletHandler().addFilterWithMapping(holder, pathSpec, dispatches);
    }
	public FilterHolder addFilter(Class<? extends Filter> filterClass, String pathSpec, EnumSet<DispatcherType> dispatches) {
		// 添加过滤器
		return getServletHandler().addFilterWithMapping(filterClass, pathSpec, dispatches);
    }
	public FilterHolder addFilter(String filterClass, String pathSpec, EnumSet<DispatcherType> dispatches) {
		return getServletHandler().addFilterWithMapping(filterClass, pathSpec, dispatches);
    }
	// **********************************************************************************************

    protected ServletRegistration.Dynamic dynamicHolderAdded(ServletHolder holder) {
        return holder.getRegistration();
    }
    protected void addRoles(String... roleNames) {
		if (_securityHandler != null && _securityHandler instanceof ConstraintAware) {
            HashSet<String> union = new HashSet<String>();
            Set<String> existing = ((ConstraintAware)_securityHandler).getRoles();
			if (existing != null) {
                union.addAll(existing);
			}
            union.addAll(Arrays.asList(roleNames));
            ((ConstraintSecurityHandler)_securityHandler).setRoles(union);
        }
    }
	public Set<String> setServletSecurity(ServletRegistration.Dynamic registration, ServletSecurityElement servletSecurityElement) {    
        Collection<String> pathSpecs = registration.getMappings();
		if (pathSpecs != null) {
			for (String pathSpec : pathSpecs) {
                List<ConstraintMapping> mappings = ConstraintSecurityHandler.createConstraintsWithMappingsForPath(registration.getName(), pathSpec, servletSecurityElement);
				for (ConstraintMapping m : mappings) {
					((ConstraintAware) getSecurityHandler()).addConstraintMapping(m);
				}
            }
        }
        return Collections.emptySet();
    }

    @Override
	public void callContextInitialized(ServletContextListener l, ServletContextEvent e) {
		try {
			if (isProgrammaticListener(l)) {
				this.getServletContext().setEnabled(false);
			}
            super.callContextInitialized(l, e);
		} finally {
            this.getServletContext().setEnabled(true);
        }
    }
    @Override
	public void callContextDestroyed(ServletContextListener l, ServletContextEvent e) {
        super.callContextDestroyed(l, e);
    }

	private boolean replaceHandler(Handler handler, Handler replace) {
        HandlerWrapper wrapper=this;
		while (true) {
			if (wrapper.getHandler() == handler) {
                doSetHandler(wrapper,replace);
                return true;
            }
			if (!(wrapper.getHandler() instanceof HandlerWrapper)) {
				return false;
			}
            wrapper = (HandlerWrapper)wrapper.getHandler();
        }
    }
	public void setSessionHandler(SessionHandler sessionHandler) {
		if (isStarted()) {
			throw new IllegalStateException("STARTED");
		}
        Handler next=null;
		if (_sessionHandler != null) {
            next=_sessionHandler.getHandler();
            _sessionHandler.setHandler(null);
            replaceHandler(_sessionHandler,sessionHandler);
        }
        _sessionHandler = sessionHandler;
		if (next != null && _sessionHandler.getHandler() == null) {
			_sessionHandler.setHandler(next);
		}
        relinkHandlers();
    }
	public void setSecurityHandler(SecurityHandler securityHandler) {
		if (isStarted()) {
			throw new IllegalStateException("STARTED");
		}

        Handler next=null;
		if (_securityHandler != null) {
            next=_securityHandler.getHandler();
            _securityHandler.setHandler(null);
            replaceHandler(_securityHandler,securityHandler);
        }
        
        _securityHandler = securityHandler;
		if (next != null && _securityHandler.getHandler() == null) {
			_securityHandler.setHandler(next);
		}
        relinkHandlers();
    }

	public void setGzipHandler(GzipHandler gzipHandler) {
		if (isStarted()) {
            throw new IllegalStateException("STARTED");
		}
		Handler next = null;
		if (_gzipHandler != null) {
			next = _gzipHandler.getHandler();
            _gzipHandler.setHandler(null);
            replaceHandler(_gzipHandler,gzipHandler);
        }
        _gzipHandler = gzipHandler;
		if (next != null && _gzipHandler.getHandler() == null) {
            _gzipHandler.setHandler(next);
		}
        relinkHandlers();
    }
	public void setServletHandler(ServletHandler servletHandler) {
		if (isStarted()) {
            throw new IllegalStateException("STARTED");
		}
		Handler next = null;
		if (_servletHandler != null) {
            next=_servletHandler.getHandler();
            _servletHandler.setHandler(null);
            replaceHandler(_servletHandler,servletHandler);
        }
        _servletHandler = servletHandler;
		if (next != null && _servletHandler.getHandler() == null) {
			_servletHandler.setHandler(next);
		}
        relinkHandlers();
    }
    @Override
	public void insertHandler(HandlerWrapper handler) {
		if (handler instanceof SessionHandler) {
			setSessionHandler((SessionHandler) handler);
		} else if (handler instanceof SecurityHandler) {
			setSecurityHandler((SecurityHandler) handler);
		} else if (handler instanceof GzipHandler) {
			setGzipHandler((GzipHandler) handler);
		} else if (handler instanceof ServletHandler) {
			setServletHandler((ServletHandler) handler);
		} else {
            HandlerWrapper tail = handler;
			while (tail.getHandler() instanceof HandlerWrapper) {
				tail = (HandlerWrapper) tail.getHandler();
			}
			if (tail.getHandler() != null) {
				throw new IllegalArgumentException("bad tail of inserted wrapper chain");
			}
            HandlerWrapper h=this;
			while (h.getHandler() instanceof HandlerWrapper) {
                HandlerWrapper wrapper = (HandlerWrapper)h.getHandler();
				if (wrapper instanceof SessionHandler || wrapper instanceof SecurityHandler
						|| wrapper instanceof ServletHandler) {
					break;
				}
				h = wrapper;
            }
			Handler next = h.getHandler();
			doSetHandler(h, handler);
			doSetHandler(tail, next);
        }
        relinkHandlers();
    }
	public DecoratedObjectFactory getObjectFactory() {
        return _objFactory;
    }
    @Deprecated
	public List<Decorator> getDecorators() {
        List<Decorator> ret = new ArrayList<ServletContextHandler.Decorator>();
		for (org.eclipse.jetty.util.Decorator decorator : _objFactory) {
            ret.add(new LegacyDecorator(decorator));
        }
        return Collections.unmodifiableList(ret);
    }
    @Deprecated
	public void setDecorators(List<Decorator> decorators) {
        _objFactory.setDecorators(decorators);
    }
    @Deprecated
	public void addDecorator(Decorator decorator) {
        _objFactory.addDecorator(decorator);
    }
	void destroyServlet(Servlet servlet) {
        _objFactory.destroy(servlet);
    }
	void destroyFilter(Filter filter) {
        _objFactory.destroy(filter);
    }

	public static class JspPropertyGroup implements JspPropertyGroupDescriptor {

        private List<String> _urlPatterns = new ArrayList<String>();
        private String _elIgnored;
        private String _pageEncoding;
        private String _scriptingInvalid;
        private String _isXml;
        private List<String> _includePreludes = new ArrayList<String>();
        private List<String> _includeCodas = new ArrayList<String>();
        private String _deferredSyntaxAllowedAsLiteral;
        private String _trimDirectiveWhitespaces;
        private String _defaultContentType;
        private String _buffer;
        private String _errorOnUndeclaredNamespace;


		public void addUrlPattern(String s) {
			if (!_urlPatterns.contains(s)) {
                _urlPatterns.add(s);
			}
        }
		public void setElIgnored(String s) {
            _elIgnored = s;
        }
		public void setPageEncoding(String pageEncoding) {
            _pageEncoding = pageEncoding;
        }
		public void setScriptingInvalid(String scriptingInvalid) {
            _scriptingInvalid = scriptingInvalid;
        }
		public void setIsXml(String isXml) {
            _isXml = isXml;
        }
		public void setDeferredSyntaxAllowedAsLiteral(String deferredSyntaxAllowedAsLiteral) {
            _deferredSyntaxAllowedAsLiteral = deferredSyntaxAllowedAsLiteral;
        }
		public void setTrimDirectiveWhitespaces(String trimDirectiveWhitespaces) {
            _trimDirectiveWhitespaces = trimDirectiveWhitespaces;
        }
		public void setDefaultContentType(String defaultContentType) {
            _defaultContentType = defaultContentType;
        }
		public void setBuffer(String buffer) {
            _buffer = buffer;
        }
		public void setErrorOnUndeclaredNamespace(String errorOnUndeclaredNamespace) {
            _errorOnUndeclaredNamespace = errorOnUndeclaredNamespace;
        }
		public void addIncludePrelude(String prelude) {
			if (!_includePreludes.contains(prelude)) {
				_includePreludes.add(prelude);
			}
		}
		public void addIncludeCoda(String coda) {
			if (!_includeCodas.contains(coda)) {
				_includeCodas.add(coda);
			}
		}

		// **********************************************************************************************
		@Override
		public Collection<String> getUrlPatterns() {
			return new ArrayList<String>(_urlPatterns);
		}
		@Override
		public String getElIgnored() {
			return _elIgnored;
		}
		@Override
		public String getPageEncoding() {
			return _pageEncoding;
		}
		@Override
		public String getScriptingInvalid() {
            return _scriptingInvalid;
        }
        @Override
		public String getIsXml() {
            return _isXml;
        }
        @Override
		public Collection<String> getIncludePreludes() {
            return new ArrayList<String>(_includePreludes); //must be a copy
        }
        @Override
		public Collection<String> getIncludeCodas() {
            return new ArrayList<String>(_includeCodas); //must be a copy
        }
        @Override
		public String getDeferredSyntaxAllowedAsLiteral() {
            return _deferredSyntaxAllowedAsLiteral;
        }
        @Override
		public String getTrimDirectiveWhitespaces() {
            return _trimDirectiveWhitespaces;
        }
        @Override
		public String getDefaultContentType() {
            return _defaultContentType;
        }
        @Override
		public String getBuffer() {
            return _buffer;
        }
        @Override
		public String getErrorOnUndeclaredNamespace() {
            return _errorOnUndeclaredNamespace;
        }
        @Override
		public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("JspPropertyGroupDescriptor:");
			sb.append(" el-ignored=" + _elIgnored);
			sb.append(" is-xml=" + _isXml);
			sb.append(" page-encoding=" + _pageEncoding);
			sb.append(" scripting-invalid=" + _scriptingInvalid);
			sb.append(" deferred-syntax-allowed-as-literal=" + _deferredSyntaxAllowedAsLiteral);
			sb.append(" trim-directive-whitespaces" + _trimDirectiveWhitespaces);
			sb.append(" default-content-type=" + _defaultContentType);
			sb.append(" buffer=" + _buffer);
			sb.append(" error-on-undeclared-namespace=" + _errorOnUndeclaredNamespace);
			for (String prelude : _includePreludes) {
				sb.append(" include-prelude=" + prelude);
			}
			for (String coda : _includeCodas) {
				sb.append(" include-coda=" + coda);
			}
            return sb.toString();
        }
		// **********************************************************************************************
    }
	public static class TagLib implements TaglibDescriptor {

        private String _uri;
        private String _location;

		public void setTaglibLocation(String location) {
			_location = location;
		}
		public void setTaglibURI(String uri) {
			_uri = uri;
		}

		// **********************************************************************************************
        @Override
		public String getTaglibURI() {
           return _uri;
        }
        @Override
		public String getTaglibLocation() {
            return _location;
        }
        @Override
		public String toString() {
			return ("TagLibDescriptor: taglib-uri=" + _uri + " location=" + _location);
        }
		// **********************************************************************************************
    }
	public static class JspConfig implements JspConfigDescriptor {

        private List<TaglibDescriptor> _taglibs = new ArrayList<TaglibDescriptor>();
        private List<JspPropertyGroupDescriptor> _jspPropertyGroups = new ArrayList<JspPropertyGroupDescriptor>();

		// **********************************************************************************************
		public JspConfig() {

		}

		// **********************************************************************************************
		public void addJspPropertyGroup(JspPropertyGroupDescriptor g) {
			_jspPropertyGroups.add(g);
		}
		public void addTaglibDescriptor(TaglibDescriptor d) {
			_taglibs.add(d);
		}
		// **********************************************************************************************
		@Override
		public Collection<TaglibDescriptor> getTaglibs() {
            return new ArrayList<TaglibDescriptor>(_taglibs);
        }
        @Override
		public Collection<JspPropertyGroupDescriptor> getJspPropertyGroups() {
           return new ArrayList<JspPropertyGroupDescriptor>(_jspPropertyGroups);
        }
        @Override
		public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("JspConfigDescriptor: \n");
			for (TaglibDescriptor taglib : _taglibs) {
				sb.append(taglib + "\n");
			}
			for (JspPropertyGroupDescriptor jpg : _jspPropertyGroups) {
				sb.append(jpg + "\n");
			}
            return sb.toString();
        }
		// **********************************************************************************************
    }
	public class Context extends ContextHandler.Context {// 表示SERVLET中的上下文环境

		@Override
		public RequestDispatcher getNamedDispatcher(String name) {

			ContextHandler context = org.eclipse.jetty.servlet.ServletContextHandler.this;

			if (_servletHandler == null) {
                return null;
			}

			ServletHolder holder = _servletHandler.getServlet(name);

			if (holder == null || !holder.isEnabled()) {
                return null;
			}

			return new Dispatcher(context, name);
        }
		@Override
		public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {// since servlet-api-3.0
			if (isStarted()) {
                throw new IllegalStateException();
			}
			if (filterName == null || "".equals(filterName.trim())) {
                throw new IllegalStateException("Missing filter name");
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            FilterHolder holder = handler.getFilter(filterName);
			if (holder == null) {
				holder = handler.newFilterHolder(Source.JAVAX_API);
                holder.setName(filterName);
                holder.setHeldClass(filterClass);
                handler.addFilter(holder);
                return holder.getRegistration();
            }
			if (holder.getClassName() == null && holder.getHeldClass() == null) {
                holder.setHeldClass(filterClass);
                return holder.getRegistration();
			} else {
				return null;
            }
        }
        @Override
		public FilterRegistration.Dynamic addFilter(String filterName, String className) {// since servlet-api-3.0
			if (isStarted()) {
                throw new IllegalStateException();
			}
			if (filterName == null || "".equals(filterName.trim())) {
                throw new IllegalStateException("Missing filter name");
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            FilterHolder holder = handler.getFilter(filterName);
			if (holder == null) {
				holder = handler.newFilterHolder(Source.JAVAX_API);
                holder.setName(filterName);
                holder.setClassName(className);
                handler.addFilter(holder);
                return holder.getRegistration();
            }
			if (holder.getClassName() == null && holder.getHeldClass() == null) {
				holder.setClassName(className);
                return holder.getRegistration();
			} else {
				return null;
            }
        }
        @Override
		public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {//since servlet-api-3.0
			if (isStarted()) {
                throw new IllegalStateException();
			}
			if (filterName == null || "".equals(filterName.trim())) {
                throw new IllegalStateException("Missing filter name");
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            FilterHolder holder = handler.getFilter(filterName);
			if (holder == null) {
				holder = handler.newFilterHolder(Source.JAVAX_API);
                holder.setName(filterName);
                holder.setFilter(filter);
                handler.addFilter(holder);
                return holder.getRegistration();
            }
			if (holder.getClassName() == null && holder.getHeldClass() == null) {
				holder.setFilter(filter);
                return holder.getRegistration();
			} else {
				return null;
            }
        }
        @Override
		public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {//since servlet-api-3.0
			if (!isStarting()) {
                throw new IllegalStateException();
			}
			if (servletName == null || "".equals(servletName.trim())) {
                throw new IllegalStateException("Missing servlet name");
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            ServletHolder holder = handler.getServlet(servletName);
			if (holder == null) {
				holder = handler.newServletHolder(Source.JAVAX_API);
                holder.setName(servletName);
                holder.setHeldClass(servletClass);
                handler.addServlet(holder);
                return dynamicHolderAdded(holder);
            }
			if (holder.getClassName() == null && holder.getHeldClass() == null) {
                holder.setHeldClass(servletClass);
                return holder.getRegistration();
			} else {
				return null;
            }
        }
        @Override
		public ServletRegistration.Dynamic addServlet(String servletName, String className) {
            if (!isStarting()){
                throw new IllegalStateException();
            }
            if (servletName == null || "".equals(servletName.trim())){
                throw new IllegalStateException("Missing servlet name");
            }
            if (!_enabled){
                throw new UnsupportedOperationException();
            }
            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            ServletHolder holder = handler.getServlet(servletName);
            if (holder == null) {
				holder = handler.newServletHolder(Source.JAVAX_API);
                holder.setName(servletName);
                holder.setClassName(className);
                handler.addServlet(holder);
                return dynamicHolderAdded(holder);
            }
			if (holder.getClassName() == null && holder.getHeldClass() == null) {
                holder.setClassName(className);
                return holder.getRegistration();
            } else {
				return null;
            }
        }
        @Override
		public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
			if (!isStarting()) {
                throw new IllegalStateException();
			}
			if (servletName == null || "".equals(servletName.trim())) {
                throw new IllegalStateException("Missing servlet name");
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            ServletHolder holder = handler.getServlet(servletName);
			if (holder == null) {
                holder = handler.newServletHolder(Source.JAVAX_API);
                holder.setName(servletName);
                holder.setServlet(servlet);
                handler.addServlet(holder);
                return dynamicHolderAdded(holder);
            }
			if (holder.getClassName() == null && holder.getHeldClass() == null) {
                holder.setServlet(servlet);
                return holder.getRegistration();
			} else {
				return null;
            }
        }
        @Override
		public boolean setInitParameter(String name, String value) {
			if (!isStarting()) {
                throw new IllegalStateException();
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
			return super.setInitParameter(name, value);
        }
        @Override
		public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
			try {
                T f = createInstance(c);
                f = _objFactory.decorate(f);
                return f;
			} catch (Exception e) {
                throw new ServletException(e);
            }
        }
        @Override
		public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {// 创建对应的SERVLET
			try {
                T s = createInstance(c);
                s = _objFactory.decorate(s);
                return s;
			} catch (Exception e) {
                throw new ServletException(e);
            }
        }
        @Override
		public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
			if (_sessionHandler != null) {
                return _sessionHandler.getSessionManager().getDefaultSessionTrackingModes();
			}
            return null;
        }
        @Override
		public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
			if (_sessionHandler != null) {
                return _sessionHandler.getSessionManager().getEffectiveSessionTrackingModes();
			}
            return null;
        }
        @Override
		public FilterRegistration getFilterRegistration(String filterName) {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
			final FilterHolder holder = ServletContextHandler.this.getServletHandler().getFilter(filterName);
			return (holder == null) ? null : holder.getRegistration();
        }
        @Override
		public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            HashMap<String, FilterRegistration> registrations = new HashMap<String, FilterRegistration>();
			ServletHandler handler = ServletContextHandler.this.getServletHandler();
			FilterHolder[] holders = handler.getFilters();
			if (holders != null) {
				for (FilterHolder holder : holders) {
					registrations.put(holder.getName(), holder.getRegistration());
				}
            }
            return registrations;
        }
        @Override
		public ServletRegistration getServletRegistration(String servletName) {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
			final ServletHolder holder = ServletContextHandler.this.getServletHandler().getServlet(servletName);
			return (holder == null) ? null : holder.getRegistration();
        }
        @Override
		public Map<String, ? extends ServletRegistration> getServletRegistrations() {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            HashMap<String, ServletRegistration> registrations = new HashMap<String, ServletRegistration>();
            ServletHandler handler=ServletContextHandler.this.getServletHandler();
            ServletHolder[] holders=handler.getServlets();
			if (holders != null) {
				for (ServletHolder holder : holders) {
                    registrations.put(holder.getName(),holder.getRegistration());
				}
            }
            return registrations;
        }
        @Override
		public SessionCookieConfig getSessionCookieConfig() {
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
			if (_sessionHandler != null) {
                return _sessionHandler.getSessionManager().getSessionCookieConfig();
			}
            return null;
        }
        @Override
		public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
			if (!isStarting()) {
                throw new IllegalStateException();
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
			if (_sessionHandler != null) {
                _sessionHandler.getSessionManager().setSessionTrackingModes(sessionTrackingModes);
			}
        }
        @Override
		public void addListener(String className) {
			if (!isStarting()) {
                throw new IllegalStateException();
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            super.addListener(className);
        }
        @Override
		public <T extends EventListener> void addListener(T t) {
			if (!isStarting()) {
                throw new IllegalStateException();
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            super.addListener(t);
            ListenerHolder holder = getServletHandler().newListenerHolder(Source.JAVAX_API);
            holder.setListener(t);
            getServletHandler().addListener(holder);
        }
        @Override
		public void addListener(Class<? extends EventListener> listenerClass) {
			if (!isStarting()) {
                throw new IllegalStateException();
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            super.addListener(listenerClass);
        }
        @Override
		public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
			try {
                T l = createInstance(clazz);
                l = _objFactory.decorate(l);
                return l;
			} catch (Exception e) {
                throw new ServletException(e);
            }
        }
        @Override
		public JspConfigDescriptor getJspConfigDescriptor() {
            return _jspConfig;
        }
        @Override
		public void setJspConfigDescriptor(JspConfigDescriptor d) {
            _jspConfig = d;
        }
        @Override
		public void declareRoles(String... roleNames) {
			if (!isStarting()) {
                throw new IllegalStateException();
			}
			if (!_enabled) {
                throw new UnsupportedOperationException();
			}
            addRoles(roleNames);
        }
    }
    @Deprecated
	public interface Decorator extends org.eclipse.jetty.util.Decorator {

    }

	private static class LegacyDecorator implements Decorator {

        private org.eclipse.jetty.util.Decorator decorator;

		public LegacyDecorator(org.eclipse.jetty.util.Decorator decorator) {
            this.decorator = decorator;
        }

		// **********************************************************************************************
        @Override
		public <T> T decorate(T o) {
            return decorator.decorate(o);
        }
        @Override
		public void destroy(Object o) {
            decorator.destroy(o);
        }
		// **********************************************************************************************
    }
}
