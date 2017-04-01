package javax.servlet;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import javax.servlet.descriptor.JspConfigDescriptor;

public interface ServletContext {// 表示指定SERVLET所在的上下文环境

    public static final String TEMPDIR = "javax.servlet.context.tempdir";
    public static final String ORDERED_LIBS = "javax.servlet.context.orderedLibs";

    public String getContextPath();

    public ServletContext getContext(String uripath);

    public int getMajorVersion();
    public int getMinorVersion();

    public int getEffectiveMajorVersion();
    public int getEffectiveMinorVersion();

    public String getMimeType(String file);

    public Set<String> getResourcePaths(String path);
    public URL getResource(String path) throws MalformedURLException;
    public InputStream getResourceAsStream(String path);
    public RequestDispatcher getRequestDispatcher(String path);
    public RequestDispatcher getNamedDispatcher(String name);
    public Servlet getServlet(String name) throws ServletException;
    public Enumeration<Servlet> getServlets();
    public Enumeration<String> getServletNames();

    public void log(String msg);
    public void log(Exception exception, String msg);
    public void log(String message, Throwable throwable);

    public String getRealPath(String path);
    public String getServerInfo();
    public String getInitParameter(String name);
    public Enumeration<String> getInitParameterNames();
    public boolean setInitParameter(String name, String value);
    public Object getAttribute(String name);
    public Enumeration<String> getAttributeNames();
    public void setAttribute(String name, Object object);
    public void removeAttribute(String name);
    public String getServletContextName();

    public ServletRegistration.Dynamic addServlet(String servletName, String className);
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet);
    public ServletRegistration.Dynamic addServlet(String servletName, Class <? extends Servlet> servletClass);

    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException;
    public ServletRegistration getServletRegistration(String servletName);
    public Map<String, ? extends ServletRegistration> getServletRegistrations();

    public FilterRegistration.Dynamic addFilter(String filterName, String className);
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter);
    public FilterRegistration.Dynamic addFilter(String filterName, Class <? extends Filter> filterClass);

    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException;
    public FilterRegistration getFilterRegistration(String filterName);
    public Map<String, ? extends FilterRegistration> getFilterRegistrations();
    public SessionCookieConfig getSessionCookieConfig();
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes);

    public Set<SessionTrackingMode> getDefaultSessionTrackingModes();
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes();

    public void addListener(String className);
    public <T extends EventListener> void addListener(T t);
    public void addListener(Class <? extends EventListener> listenerClass);
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException;
    public JspConfigDescriptor getJspConfigDescriptor();
    public ClassLoader getClassLoader();
    public void declareRoles(String... roleNames);
    public String getVirtualServerName();
}
