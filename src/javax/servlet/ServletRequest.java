package javax.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

public interface ServletRequest {// 表示对该SERVLET的请求(共37个接口函数)

	// 涉及到的内容有:
	// Attribute
	// CharacterEncoding
	// ContentLength
	// ContentType
	// ServletInputStream
	// Parameter
	// Protocol
	// Scheme
	// ServerName
	// ServerPort
	// Reader

	// 表示该请求关联的属性对象
	public Object getAttribute(String name);
    public Enumeration<String> getAttributeNames();
	public void setAttribute(String name, Object o);
	public void removeAttribute(String name);

    public String getCharacterEncoding();
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException;

    public int getContentLength();
    public long getContentLengthLong();

    public String getContentType();

    public ServletInputStream getInputStream() throws IOException; 

    public String getParameter(String name);
    public Enumeration<String> getParameterNames();
    public String[] getParameterValues(String name);
    public Map<String, String[]> getParameterMap();

    public BufferedReader getReader() throws IOException;

    public String getRemoteAddr();
    public String getRemoteHost();

    public Locale getLocale();
    public Enumeration<Locale> getLocales();

    public boolean isSecure();

	public RequestDispatcher getRequestDispatcher(String path);// 返回派发器

    public String getRealPath(String path);

    public int getRemotePort();
    public String getLocalName();
    public String getLocalAddr();
    public int getLocalPort();

	public String getProtocol();// 对应的协议版本(例如当为HTTP时表示HTTP的版本信息)
	public String getScheme();
	public String getServerName();
	public int getServerPort();
    public ServletContext getServletContext();

	// ***********************************************************************
	// 开启异步调用并生成对应的上下文对象--获取的都是同一个对象
    public AsyncContext startAsync() throws IllegalStateException;
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException;
    public boolean isAsyncStarted();
    public boolean isAsyncSupported();
    public AsyncContext getAsyncContext();

	// ***********************************************************************
    public DispatcherType getDispatcherType();
}

