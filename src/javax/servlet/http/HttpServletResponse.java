package javax.servlet.http;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletResponse;

public interface HttpServletResponse extends ServletResponse {// 增加了一些HTTP相关的东西

    public void addCookie(Cookie cookie);

	public boolean containsHeader(String name);// 判断请求头中是否包含指定域

    public String encodeURL(String url);
    public String encodeRedirectURL(String url);
    public String encodeUrl(String url);
    public String encodeRedirectUrl(String url);

	public void sendError(int sc, String msg) throws IOException;// 直接发送错误信息给客户端
	public void sendError(int sc) throws IOException;// 直接发送错误码给客户端

    public void sendRedirect(String location) throws IOException;

	public void setDateHeader(String name, long date);// 设置
    public void addDateHeader(String name, long date);

    public void setHeader(String name, String value);
    public void addHeader(String name, String value);

    public void setIntHeader(String name, int value);
    public void addIntHeader(String name, int value);

	public void setStatus(int sc);// 设置响应的状态码
    public void setStatus(int sc, String sm);
    public int getStatus();

	public String getHeader(String name);

	public Collection<String> getHeaders(String name);
    public Collection<String> getHeaderNames();
    
	// ***************************************************************************************************
	// 下面是各种状态码
    public static final int SC_CONTINUE = 100;
	public static final int SC_SWITCHING_PROTOCOLS = 101;
    public static final int SC_OK = 200;
    public static final int SC_CREATED = 201;
    public static final int SC_ACCEPTED = 202;
    public static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;
    public static final int SC_NO_CONTENT = 204;
    public static final int SC_RESET_CONTENT = 205;
    public static final int SC_PARTIAL_CONTENT = 206;
    public static final int SC_MULTIPLE_CHOICES = 300;
    public static final int SC_MOVED_PERMANENTLY = 301;
	public static final int SC_MOVED_TEMPORARILY = 302;// 用于重定向
    public static final int SC_FOUND = 302;
    public static final int SC_SEE_OTHER = 303;
	public static final int SC_NOT_MODIFIED = 304;// 表示HTTP请求的内容没有发生变化
    public static final int SC_USE_PROXY = 305;
    public static final int SC_TEMPORARY_REDIRECT = 307;
	public static final int SC_BAD_REQUEST = 400;//
    public static final int SC_UNAUTHORIZED = 401;
    public static final int SC_PAYMENT_REQUIRED = 402;
    public static final int SC_FORBIDDEN = 403;
    public static final int SC_NOT_FOUND = 404;
	public static final int SC_METHOD_NOT_ALLOWED = 405;//
    public static final int SC_NOT_ACCEPTABLE = 406;
    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;
    public static final int SC_REQUEST_TIMEOUT = 408;
    public static final int SC_CONFLICT = 409;
    public static final int SC_GONE = 410;
    public static final int SC_LENGTH_REQUIRED = 411;
    public static final int SC_PRECONDITION_FAILED = 412;
    public static final int SC_REQUEST_ENTITY_TOO_LARGE = 413;
    public static final int SC_REQUEST_URI_TOO_LONG = 414;
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    public static final int SC_EXPECTATION_FAILED = 417;
    public static final int SC_INTERNAL_SERVER_ERROR = 500;
	public static final int SC_NOT_IMPLEMENTED = 501;// 表示服务器端还未实现该协议
    public static final int SC_BAD_GATEWAY = 502;
    public static final int SC_SERVICE_UNAVAILABLE = 503;
    public static final int SC_GATEWAY_TIMEOUT = 504;
    public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;
	// ***************************************************************************************************
}
