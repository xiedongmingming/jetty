package javax.servlet.http;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;

public interface HttpServletRequest extends ServletRequest {// 接口(共32个接口函数)用于提供请求信息(由容器创建并传递给处理函数)

    public static final String BASIC_AUTH = "BASIC";
    public static final String FORM_AUTH = "FORM";
    public static final String CLIENT_CERT_AUTH = "CLIENT_CERT";
    public static final String DIGEST_AUTH = "DIGEST";

    public String getAuthType();
    public Cookie[] getCookies();

	public long getDateHeader(String name);// 用于获取HTTP请求头中的参数

    public String getHeader(String name); 
    public Enumeration<String> getHeaders(String name); 

	public Enumeration<String> getHeaderNames();// 表示请求头参数
    public int getIntHeader(String name);

	public String getMethod();// 表示HTTP请求的方法(GET、PUT等)

    public String getPathInfo();
    public String getPathTranslated();
    public String getContextPath();
    public String getQueryString();
    public String getRemoteUser();
    public boolean isUserInRole(String role);
    public java.security.Principal getUserPrincipal();
    public String getRequestedSessionId();

	public String getRequestURI();// 表示该请求对应的额URI字符
    public StringBuffer getRequestURL();
    public String getServletPath();

    public HttpSession getSession(boolean create);
    public HttpSession getSession();

    public String changeSessionId();
    public boolean isRequestedSessionIdValid();
    public boolean isRequestedSessionIdFromCookie();
    public boolean isRequestedSessionIdFromURL();
    public boolean isRequestedSessionIdFromUrl();

    public boolean authenticate(HttpServletResponse response) throws IOException,ServletException;
    public void login(String username, String password) throws ServletException;
    public void logout() throws ServletException;
    public Collection<Part> getParts() throws IOException, ServletException;
    public Part getPart(String name) throws IOException, ServletException;
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException;
}
