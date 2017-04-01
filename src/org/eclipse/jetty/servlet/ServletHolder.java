package org.eclipse.jetty.servlet;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.servlet.MultipartConfigElement;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletSecurityElement;
import javax.servlet.SingleThreadModel;
import javax.servlet.UnavailableException;

import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.RunAsToken;
import org.eclipse.jetty.server.MultiPartCleanerListener;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ServletHolder extends Holder<Servlet> implements UserIdentity.Scope, Comparable<ServletHolder> {// 用于包装一个SERVLET

    private static final Logger LOG = Log.getLogger(ServletHolder.class);

    private int _initOrder = -1;
	private boolean _initOnStartup = false;
    private boolean _initialized = false;
    private Map<String, String> _roleMap;
    private String _forcedPath;
    private String _runAsRole;
    private RunAsToken _runAsToken;
    private IdentityService _identityService;
    private ServletRegistration.Dynamic _registration;
    private JspContainer _jspContainer;

	private transient Servlet _servlet;// 对应的SERVLET
	private transient Config _config;// SERVLET对应的配置文件
    private transient long _unavailable;
    private transient boolean _enabled = true;
    private transient UnavailableException _unavailableEx;

    public static final String APACHE_SENTINEL_CLASS = "org.apache.tomcat.InstanceManager";
    public static final  String JSP_GENERATED_PACKAGE_NAME = "org.eclipse.jetty.servlet.jspPackagePrefix";
    public static final Map<String,String> NO_MAPPED_ROLES = Collections.emptyMap();
    public static enum JspContainer {APACHE, OTHER};

	// *************************************************************************************
	public ServletHolder() {
        this(Source.EMBEDDED);
    }
	public ServletHolder(Holder.Source creator) {
        super(creator);
    }
	public ServletHolder(Servlet servlet) {
        this(Source.EMBEDDED);
        setServlet(servlet);
    }
	public ServletHolder(String name, Class<? extends Servlet> servlet) {
        this(Source.EMBEDDED);
        setName(name);
        setHeldClass(servlet);
    }
	public ServletHolder(String name, Servlet servlet) {
        this(Source.EMBEDDED);
        setName(name);
        setServlet(servlet);
    }
	public ServletHolder(Class<? extends Servlet> servlet) {
        this(Source.EMBEDDED);
        setHeldClass(servlet);
    }
	// *************************************************************************************

	public UnavailableException getUnavailableException() {
        return _unavailableEx;
    }
	public synchronized void setServlet(Servlet servlet) {
		if (servlet == null || servlet instanceof SingleThreadModel) {
			throw new IllegalArgumentException();
		}
		_extInstance = true;
		_servlet = servlet;
        setHeldClass(servlet.getClass());
		if (getName() == null) {
			setName(servlet.getClass().getName() + "-" + super.hashCode());
		}
    }
	public int getInitOrder() {
        return _initOrder;
    }
	public void setInitOrder(int order) {
		_initOnStartup = order >= 0;
        _initOrder = order;
    }
    @Override
	public int compareTo(ServletHolder sh) {
		if (sh == this) {
			return 0;
		}
		if (sh._initOrder < _initOrder) {
			return 1;
		}
		if (sh._initOrder > _initOrder) {
			return -1;
		}
        int c;
		if (_className == null && sh._className == null) {
			c = 0;
		} else if (_className == null) {
			c = -1;
		} else if (sh._className == null) {
			c = 1;
		} else {
			c = _className.compareTo(sh._className);
		}
		if (c == 0) {
			c = _name.compareTo(sh._name);
		}
        return c;
    }
    @Override
	public boolean equals(Object o) {
		return o instanceof ServletHolder && compareTo((ServletHolder) o) == 0;
    }
    @Override
	public int hashCode() {
		return _name == null ? System.identityHashCode(this) : _name.hashCode();
    }
	public synchronized void setUserRoleLink(String name, String link) {
		if (_roleMap == null) {
			_roleMap = new HashMap<String, String>();
		}
		_roleMap.put(name, link);
    }
	public String getUserRoleLink(String name) {
		if (_roleMap == null) {
			return name;
		}
		String link = _roleMap.get(name);
		return (link == null) ? name : link;
    }
    public String getForcedPath()    {
        return _forcedPath;
    }
    public void setForcedPath(String forcedPath)    {
        _forcedPath = forcedPath;
    }
    public boolean isEnabled()    {
        return _enabled;
    }
   public void setEnabled(boolean enabled)    {
        _enabled = enabled;
    }
    @Override
	public void doStart() throws Exception {
		_unavailable = 0;
		if (!_enabled) {
			return;
		}
		if (_forcedPath != null) {
			String precompiled = getClassNameForJsp(_forcedPath);
			ServletHolder jsp = getServletHandler().getServlet(precompiled);
			if (jsp != null && jsp.getClassName() != null) {
                setClassName(jsp.getClassName());
			} else {
				if (getClassName() == null) {
                    jsp=getServletHandler().getServlet("jsp");
					if (jsp != null) {
                        setClassName(jsp.getClassName());
						for (Map.Entry<String, String> entry : jsp.getInitParameters().entrySet()) {
							if (!_initParams.containsKey(entry.getKey())) {
								setInitParameter(entry.getKey(), entry.getValue());
							}
                        }
                        setInitParameter("jspFile", _forcedPath);
                    }
                }
            }
        }
		try {
            super.doStart();
		} catch (UnavailableException ue) {
            makeUnavailable(ue);
			if (_servletHandler.isStartWithUnavailable()) {
                LOG.ignore(ue);
                return;
			} else {
				throw ue;
            }
        }
		try {
            checkServletType();
		} catch (UnavailableException ue) {
            makeUnavailable(ue);
			if (_servletHandler.isStartWithUnavailable()) {
                LOG.ignore(ue);
                return;
			} else {
				throw ue;
            }
        }

        checkInitOnStartup();

        _identityService = _servletHandler.getIdentityService();
		if (_identityService != null && _runAsRole != null) {
			_runAsToken = _identityService.newRunAsToken(_runAsRole);
		}
		_config = new Config();
		if (_class != null && javax.servlet.SingleThreadModel.class.isAssignableFrom(_class)) {
			_servlet = new SingleThreadedWrapper();
		}
    }
    @Override
	public void initialize() throws Exception {
        if(!_initialized){
            super.initialize();
			if (_extInstance || _initOnStartup) {
				try {
                    initServlet();
				} catch (Exception e) {
					if (_servletHandler.isStartWithUnavailable()) {
						LOG.ignore(e);
					} else {
						throw e;
					}
                }
            }
        }
        _initialized = true;
    }
    @Override
	public void doStop() throws Exception {
        Object old_run_as = null;
		if (_servlet != null) {
			try {
				if (_identityService != null) {
					old_run_as = _identityService.setRunAs(_identityService.getSystemUserIdentity(), _runAsToken);
				}
                destroyInstance(_servlet);
			} catch (Exception e) {
                LOG.warn(e);
			} finally {
				if (_identityService != null) {
					_identityService.unsetRunAs(old_run_as);
				}
            }
        }
		if (!_extInstance) {
			_servlet = null;
		}
        _config=null;
        _initialized = false;
    }
    @Override
	public void destroyInstance(Object o) throws Exception {
		if (o == null) {
			return;
		}
        Servlet servlet =  ((Servlet)o);
        getServletHandler().destroyServlet(servlet);
        servlet.destroy();
    }
	public synchronized Servlet getServlet() throws ServletException {
		if (_unavailable != 0) {
			if (_unavailable < 0 || _unavailable > 0 && System.currentTimeMillis() < _unavailable) {
				throw _unavailableEx;
			}
			_unavailable = 0;
			_unavailableEx = null;
        }
		if (_servlet == null) {
			initServlet();
		}
        return _servlet;
    }

	public Servlet getServletInstance() {
        return _servlet;
    }

	public void checkServletType() throws UnavailableException {
		if (_class == null || !javax.servlet.Servlet.class.isAssignableFrom(_class)) {
            throw new UnavailableException("Servlet "+_class+" is not a javax.servlet.Servlet");
        }
    }

	public boolean isAvailable() {
		if (isStarted() && _unavailable == 0) {
			return true;
		}
		try {
            getServlet();
		} catch (Exception e) {
            LOG.ignore(e);
        }
        return isStarted()&& _unavailable==0;
    }
	private void checkInitOnStartup() {
		if (_class == null) {
			return;
		}
		if ((_class.getAnnotation(javax.servlet.annotation.ServletSecurity.class) != null) && !_initOnStartup) {
			setInitOrder(Integer.MAX_VALUE);
		}
    }
	private void makeUnavailable(UnavailableException e) {
		if (_unavailableEx == e && _unavailable != 0) {
			return;
		}
        _servletHandler.getServletContext().log("unavailable",e);
        _unavailableEx=e;
        _unavailable=-1;
		if (e.isPermanent()) {
			_unavailable = -1;
		} else {
			if (_unavailableEx.getUnavailableSeconds() > 0) {
				_unavailable = System.currentTimeMillis() + 1000 * _unavailableEx.getUnavailableSeconds();
			} else {
				_unavailable = System.currentTimeMillis() + 5000; // TODO
																	// configure
			}
        }
    }

	@SuppressWarnings("serial")
	private void makeUnavailable(final Throwable e) {
		if (e instanceof UnavailableException) {
			makeUnavailable((UnavailableException) e);
		} else {
            ServletContext ctx = _servletHandler.getServletContext();
			if (ctx == null) {
				LOG.info("unavailable", e);
			} else {
				ctx.log("unavailable", e);
			}
			_unavailableEx = new UnavailableException(String.valueOf(e), -1) {
                {
                    initCause(e);
                }
            };
			_unavailable = -1;
        }
    }
	private void initServlet() throws ServletException {
        Object old_run_as = null;
		try {
			if (_servlet == null) {
				_servlet = newInstance();
			}
			if (_config == null) {
				_config = new Config();
			}
			if (_identityService != null) {
				old_run_as = _identityService.setRunAs(_identityService.getSystemUserIdentity(), _runAsToken);
            }
			if (isJspServlet()) {
                initJspServlet();
                detectJspContainer();
            }

            initMultiPart();

			if (_forcedPath != null && _jspContainer == null) {
                detectJspContainer();
            }
            _servlet.init(_config);
		} catch (UnavailableException e) {
            makeUnavailable(e);
            _servlet=null;
            _config=null;
            throw e;
		} catch (ServletException e) {
			makeUnavailable(e.getCause() == null ? e : e.getCause());
			_servlet = null;
			_config = null;
            throw e;
		} catch (Exception e) {
            makeUnavailable(e);
			_servlet = null;
			_config = null;
			throw new ServletException(this.toString(), e);
		} finally {
			if (_identityService != null) {
				_identityService.unsetRunAs(old_run_as);
			}
        }
    }
	protected void initJspServlet() throws Exception {

        ContextHandler ch = ContextHandler.getContextHandler(getServletHandler().getServletContext());

        ch.setAttribute("org.apache.catalina.jsp_classpath", ch.getClassPath());

		if ("?".equals(getInitParameter("classpath"))) {
            String classpath = ch.getClassPath();

			if (classpath != null) {
				setInitParameter("classpath", classpath);
			}
        }

        File scratch = null;

		if (getInitParameter("scratchdir") == null) {

            File tmp = (File)getServletHandler().getServletContext().getAttribute(ServletContext.TEMPDIR);

            scratch = new File(tmp, "jsp");

            setInitParameter("scratchdir", scratch.getAbsolutePath());
        }

        scratch = new File (getInitParameter("scratchdir"));

		if (!scratch.exists()) {
			scratch.mkdir();
		}
    }
	protected void initMultiPart() throws Exception {
		if (((Registration) getRegistration()).getMultipartConfig() != null) {
            ContextHandler ch = ContextHandler.getContextHandler(getServletHandler().getServletContext());
            ch.addEventListener(MultiPartCleanerListener.INSTANCE);
        }
    }
    @Override
	public String getContextPath() {
        return _config.getServletContext().getContextPath();
    }
    @Override
	public Map<String, String> getRoleRefMap() {
        return _roleMap;
    }
	public String getRunAsRole() {
        return _runAsRole;
    }
	public void setRunAsRole(String role) {
        _runAsRole = role;
    }

	protected void prepare(Request baseRequest, ServletRequest request, ServletResponse response)
			throws ServletException, UnavailableException {
        ensureInstance();
        MultipartConfigElement mpce = ((Registration)getRegistration()).getMultipartConfig();
		if (mpce != null) {
			baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, mpce);
		}
    }

	public synchronized Servlet ensureInstance() throws ServletException, UnavailableException {
		if (_class == null) {
			throw new UnavailableException("Servlet Not Initialized");
		}
        Servlet servlet=_servlet;
		if (!isStarted()) {
			throw new UnavailableException("Servlet not initialized", -1);
		}
		if (_unavailable != 0 || (!_initOnStartup && servlet == null)) {
			servlet = getServlet();
		}
		if (servlet == null) {
			throw new UnavailableException("Could not instantiate " + _class);
		}

        return servlet;
    }
	@SuppressWarnings("unused")
	public void handle(Request baseRequest, ServletRequest request, ServletResponse response) throws ServletException, UnavailableException, IOException {

		System.out.println("请求处理过程: 9. ServletHolder");

		if (_class == null) {
            throw new UnavailableException("Servlet Not Initialized");
		}
        Servlet servlet = ensureInstance();

        boolean servlet_error=true;
        Object old_run_as = null;
        boolean suspendable = baseRequest.isAsyncSupported();
		try {
			if (_forcedPath != null) {
				adaptForcedPathToJspContainer(request);
			}
			if (_identityService != null) {
				old_run_as = _identityService.setRunAs(baseRequest.getResolvedUserIdentity(), _runAsToken);
			}
			if (baseRequest.isAsyncSupported() && !isAsyncSupported()) {
				try {
                    baseRequest.setAsyncSupported(false,this.toString());
					servlet.service(request, response);
				} finally {
					baseRequest.setAsyncSupported(true, null);
                }
			} else {
				servlet.service(request, response);// (HttpServlet)HelloServlet.service
            }
			servlet_error = false;
		} catch (UnavailableException e) {
            makeUnavailable(e);
            throw _unavailableEx;
		} finally {
			if (_identityService != null) {
				_identityService.unsetRunAs(old_run_as);
			}
			if (servlet_error) {
				request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, getName());
			}
        }
    }

	private boolean isJspServlet() {
		if (_servlet == null) {
			return false;
		}
        Class<?> c = _servlet.getClass();
        boolean result = false;
		while (c != null && !result) {
            result = isJspServlet(c.getName());
            c = c.getSuperclass();
        }
        return result;
    }

	private boolean isJspServlet(String classname) {
		if (classname == null) {
			return false;
		}
        return ("org.apache.jasper.servlet.JspServlet".equals(classname));
    }

	private void adaptForcedPathToJspContainer(ServletRequest request) {

    }

	private void detectJspContainer() {
		if (_jspContainer == null) {
			try {
                Loader.loadClass(Holder.class, APACHE_SENTINEL_CLASS);
                _jspContainer = JspContainer.APACHE;
			} catch (ClassNotFoundException x) {
                _jspContainer = JspContainer.OTHER;
            }
        }
    }

	private String getNameOfJspClass(String jsp) {
		if (jsp == null) {
			return "";
		}

        int i = jsp.lastIndexOf('/') + 1;
        jsp = jsp.substring(i);
		try {
            Class<?> jspUtil = Loader.loadClass(Holder.class, "org.apache.jasper.compiler.JspUtil");
            Method makeJavaIdentifier = jspUtil.getMethod("makeJavaIdentifier", String.class);
            return (String)makeJavaIdentifier.invoke(null, jsp);
		} catch (Exception e) {
            String tmp = jsp.replace('.','_');
            LOG.warn("Unable to make identifier for jsp "+jsp +" trying "+tmp+" instead");
            return tmp;
        }
    }

	private String getPackageOfJspClass(String jsp) {
		if (jsp == null) {
			return "";
		}

        int i = jsp.lastIndexOf('/');
		if (i <= 0) {
			return "";
		}
		try {
            Class<?> jspUtil = Loader.loadClass(Holder.class, "org.apache.jasper.compiler.JspUtil");
            Method makeJavaPackage = jspUtil.getMethod("makeJavaPackage", String.class);
            return (String)makeJavaPackage.invoke(null, jsp.substring(0,i));
		} catch (Exception e) {
            String tmp = jsp.substring(1).replace('/','.');
            LOG.warn("Unable to make package for jsp "+jsp +" trying "+tmp+" instead");
            return tmp;
        }
    }

	private String getJspPackagePrefix() {
        String jspPackageName = getServletHandler().getServletContext().getInitParameter(JSP_GENERATED_PACKAGE_NAME );
		if (jspPackageName == null) {
			jspPackageName = "org.apache.jsp";
		}
        return jspPackageName;
    }

	private String getClassNameForJsp(String jsp) {
		if (jsp == null) {
			return null;
		}
        return getJspPackagePrefix() + "." +getPackageOfJspClass(jsp) + "." + getNameOfJspClass(jsp);
    }
	protected class Config extends HolderConfig implements ServletConfig {// SERVLET的配置文件:ServletConfig
        @Override
		public String getServletName() {
            return getName();
        }
    }
	public class Registration extends HolderRegistration implements ServletRegistration.Dynamic {
        protected MultipartConfigElement _multipartConfig;
        @Override
		public Set<String> addMapping(String... urlPatterns) {
            illegalStateIfContextStarted();
            Set<String> clash=null;
			for (String pattern : urlPatterns) {
                ServletMapping mapping = _servletHandler.getServletMapping(pattern);
				if (mapping != null) {
					if (!mapping.isDefault()) {
						if (clash == null) {
							clash = new HashSet<String>();
						}
                        clash.add(pattern);
                    }
                }
            }
			if (clash != null) {
				return clash;
			}
            ServletMapping mapping = new ServletMapping();
            mapping.setServletName(ServletHolder.this.getName());
            mapping.setPathSpecs(urlPatterns);
            _servletHandler.addServletMapping(mapping);

            return Collections.emptySet();
        }

        @Override
		public Collection<String> getMappings() {
			ServletMapping[] mappings = _servletHandler.getServletMappings();
            List<String> patterns=new ArrayList<String>();
			if (mappings != null) {
				for (ServletMapping mapping : mappings) {
					if (!mapping.getServletName().equals(getName())) {
						continue;
					}
					String[] specs = mapping.getPathSpecs();
					if (specs != null && specs.length > 0) {
						patterns.addAll(Arrays.asList(specs));
					}
                }
            }
            return patterns;
        }

        @Override
		public String getRunAsRole() {
            return _runAsRole;
        }

        @Override
		public void setLoadOnStartup(int loadOnStartup) {
            illegalStateIfContextStarted();
            ServletHolder.this.setInitOrder(loadOnStartup);
        }

		public int getInitOrder() {
            return ServletHolder.this.getInitOrder();
        }

        @Override
		public void setMultipartConfig(MultipartConfigElement element) {
            _multipartConfig = element;
        }

		public MultipartConfigElement getMultipartConfig() {
            return _multipartConfig;
        }

        @Override
		public void setRunAsRole(String role) {
            _runAsRole = role;
        }

        @Override
		public Set<String> setServletSecurity(ServletSecurityElement securityElement) {
            return _servletHandler.setServletSecurity(this, securityElement);
        }
    }

	public ServletRegistration.Dynamic getRegistration() {
		if (_registration == null) {
			_registration = new Registration();
		}
        return _registration;
    }

	private class SingleThreadedWrapper implements Servlet {
		Stack<Servlet> _stack = new Stack<Servlet>();
        @Override
		public void destroy() {
			synchronized (this) {
				while (_stack.size() > 0) {
					try {
						(_stack.pop()).destroy();
					} catch (Exception e) {
						LOG.warn(e);
					}
				}
            }
        }
        @Override
		public ServletConfig getServletConfig() {
            return _config;
        }
        @Override
		public String getServletInfo() {
            return null;
        }
        @Override
		public void init(ServletConfig config) throws ServletException {
			synchronized (this) {
				if (_stack.size() == 0) {
					try {
                        Servlet s = newInstance();
                        s.init(config);
                        _stack.push(s);
					} catch (ServletException e) {
                        throw e;
					} catch (Exception e) {
                        throw new ServletException(e);
                    }
                }
            }
        }

        @Override
		public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
            Servlet s;
			synchronized (this) {
				if (_stack.size() > 0) {
					s = _stack.pop();
				} else {
					try {
                        s = newInstance();
                        s.init(_config);
					} catch (ServletException e) {
                        throw e;
					} catch (Exception e) {
                        throw new ServletException(e);
                    }
                }
            }

			try {
                s.service(req,res);
			} finally {
				synchronized (this) {
                    _stack.push(s);
                }
            }
        }
    }
	protected Servlet newInstance() throws ServletException, IllegalAccessException, InstantiationException {
		try {
            ServletContext ctx = getServletHandler().getServletContext();
			if (ctx instanceof ServletContextHandler.Context) {
				return ((ServletContextHandler.Context) ctx).createServlet(getHeldClass());
			}
            return getHeldClass().newInstance();
		} catch (ServletException se) {
            Throwable cause = se.getRootCause();
			if (cause instanceof InstantiationException) {
				throw (InstantiationException) cause;
			}
			if (cause instanceof IllegalAccessException) {
				throw (IllegalAccessException) cause;
			}
            throw se;
        }
    }
    @Override
	public String toString() {
		return String.format("%s@%x==%s,%d,%b", _name, hashCode(), _className, _initOrder, _servlet != null);
    }
}
