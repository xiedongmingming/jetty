package javax.servlet.http;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.ResourceBundle;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;

public abstract class HttpServlet extends GenericServlet {

	// 继承结构图:
	// HttpServlet-->GenericServlet-->Servlet、ServletConfig

	private static final long serialVersionUID = 8466325577512134784L;

	// ***************************************************************************************************
	// HTTP请求的各种方法:目前七种方法
	private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_TRACE = "TRACE";
	
	// ***************************************************************************************************
	private static final String HEADER_IFMODSINCE = "If-Modified-Since";// 请求中携带的参数
	private static final String HEADER_LASTMOD = "Last-Modified";// 响应中携带的参数
	
	// ***************************************************************************************************
    private static final String LSTRING_FILE = "javax.servlet.http.LocalStrings";
    
	// ***************************************************************************************************
	private static ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

	// ***************************************************************************************************
	public HttpServlet() {//
	
	}
	// ***************************************************************************************************
	// 下面都是默认的错误处理
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String protocol = req.getProtocol();

        String msg = lStrings.getString("http.method_delete_not_supported");

		if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }
	protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		NoBodyResponse response = new NoBodyResponse(resp);// 表示只生成响应头给客户端

        doGet(req, response);

        response.setContentLength();
    }
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String protocol = req.getProtocol();

        String msg = lStrings.getString("http.method_get_not_supported");

        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		Method[] methods = getAllDeclaredMethods(this.getClass());// 获取所有声明的方法

        boolean ALLOW_GET = false;
        boolean ALLOW_HEAD = false;
        boolean ALLOW_POST = false;
        boolean ALLOW_PUT = false;
        boolean ALLOW_DELETE = false;

        boolean ALLOW_TRACE = true;
        boolean ALLOW_OPTIONS = true;

		for (int i = 0; i < methods.length; i++) {
            String methodName = methods[i].getName();
            if (methodName.equals("doGet")) {
                ALLOW_GET = true;
                ALLOW_HEAD = true;
            } else if (methodName.equals("doPost")) {
                ALLOW_POST = true;
            } else if (methodName.equals("doPut")) {
                ALLOW_PUT = true;
            } else if (methodName.equals("doDelete")) {
                ALLOW_DELETE = true;
            }
        }

        StringBuilder allow = new StringBuilder();

        if (ALLOW_GET) {
            allow.append(METHOD_GET);
        }
        if (ALLOW_HEAD) {
            if (allow.length() > 0) {
                allow.append(", ");
            }
            allow.append(METHOD_HEAD);
        }
        if (ALLOW_POST) {
            if (allow.length() > 0) {
                allow.append(", ");
            }
            allow.append(METHOD_POST);
        }
        if (ALLOW_PUT) {
            if (allow.length() > 0) {
                allow.append(", ");
            }
            allow.append(METHOD_PUT);
        }
        if (ALLOW_DELETE) {
            if (allow.length() > 0) {
                allow.append(", ");
            }
            allow.append(METHOD_DELETE);
        }
        if (ALLOW_TRACE) {
            if (allow.length() > 0) {
                allow.append(", ");
            }
            allow.append(METHOD_TRACE);
        }
        if (ALLOW_OPTIONS) {
            if (allow.length() > 0) {
                allow.append(", ");
            }
            allow.append(METHOD_OPTIONS);
        }

        resp.setHeader("Allow", allow.toString());
    }
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String protocol = req.getProtocol();

		String msg = lStrings.getString("http.method_post_not_supported");

		if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String protocol = req.getProtocol();

        String msg = lStrings.getString("http.method_put_not_supported");

        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		int responseLength;

		String CRLF = "\r\n";// 控制符

        StringBuilder buffer = new StringBuilder("TRACE ").append(req.getRequestURI()).append(" ").append(req.getProtocol());

        Enumeration<String> reqHeaderEnum = req.getHeaderNames();

        while( reqHeaderEnum.hasMoreElements() ) {

			String headerName = reqHeaderEnum.nextElement();

			buffer.append(CRLF).append(headerName).append(": ").append(req.getHeader(headerName));
        }

        buffer.append(CRLF);

        responseLength = buffer.length();

        resp.setContentType("message/http");
        resp.setContentLength(responseLength);

        ServletOutputStream out = resp.getOutputStream();

        out.print(buffer.toString());
    }

	// ***************************************************************************************************
	protected long getLastModified(HttpServletRequest req) {// 获取服务端上一次修改的时间
        return -1;
    }
	private void maybeSetLastModified(HttpServletResponse resp, long lastModified) {// 设置响应头参数
		if (resp.containsHeader(HEADER_LASTMOD)) {// 已经有了就不管了????
			return;
		}
		if (lastModified >= 0) {// 重新设置进去
			resp.setDateHeader(HEADER_LASTMOD, lastModified);
		}
    }

	// ***************************************************************************************************
	private Method[] getAllDeclaredMethods(Class<? extends HttpServlet> c) {// 获取指定类声明的方法
        Class<?> clazz = c;
        Method[] allMethods = null;
		while (!clazz.equals(HttpServlet.class)) {// 本类是一个抽象类--一般由其实现类来调用
			Method[] thisMethods = clazz.getDeclaredMethods();// 当前类的方法放在前面
            if (allMethods != null && allMethods.length > 0) {
                Method[] subClassMethods = allMethods;
                allMethods = new Method[thisMethods.length + subClassMethods.length];
                System.arraycopy(thisMethods, 0, allMethods, 0, thisMethods.length);
                System.arraycopy(subClassMethods, 0, allMethods, thisMethods.length, subClassMethods.length);
            } else {
                allMethods = thisMethods;
            }
			clazz = clazz.getSuperclass();// 父类
        }
        return ((allMethods != null) ? allMethods : new Method[0]);
    }

	// ***************************************************************************************************
    @Override
	public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {

		// 必须是HTTP的SERVLET

		HttpServletRequest request;
        HttpServletResponse response;

        if (!(req instanceof HttpServletRequest && res instanceof HttpServletResponse)) {
			throw new ServletException("non http request or response");
        }

        request = (HttpServletRequest) req;
        response = (HttpServletResponse) res;

        service(request, response);
    }
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String method = req.getMethod();// 请求方法

		if (method.equals(METHOD_GET)) {

			long lastModified = getLastModified(req);// 用于获取对应资源上一次的修改时间

			if (lastModified == -1) {// 表示不支持特性: if-modified-since

				doGet(req, resp);

			} else {// if-modified-since

				long ifModifiedSince = req.getDateHeader(HEADER_IFMODSINCE);// 表示请求端携带的上一次修改时间

				if (ifModifiedSince < lastModified) {// 表示服务器端对应资源发生变化了

					maybeSetLastModified(resp, lastModified);// 设置参数: Last-Modified

                    doGet(req, resp);

				} else {// 表示数据没有发生变化(直接返回响应)
                    resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                }
            }

        } else if (method.equals(METHOD_HEAD)) {

            long lastModified = getLastModified(req);

            maybeSetLastModified(resp, lastModified);

            doHead(req, resp);

        } else if (method.equals(METHOD_POST)) {

            doPost(req, resp);

        } else if (method.equals(METHOD_PUT)) {

            doPut(req, resp);

        } else if (method.equals(METHOD_DELETE)) {

            doDelete(req, resp);

        } else if (method.equals(METHOD_OPTIONS)) {

            doOptions(req,resp);

        } else if (method.equals(METHOD_TRACE)) {

            doTrace(req,resp);

		} else {// 表示暂时不支持该方法

            String errMsg = lStrings.getString("http.method_not_implemented");

            Object[] errArgs = new Object[1];

            errArgs[0] = method;

            errMsg = MessageFormat.format(errMsg, errArgs);

            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, errMsg);
        }
    }
}
// *********************************************************************************************************
class NoBodyResponse extends HttpServletResponseWrapper {// 响应头(表示没有响应体的HTTP响应)

	// NoBodyResponse-->HttpServletResponseWrapper-->ServletResponseWrapper(HttpServletResponse)-->ServletResponse

    private static final ResourceBundle lStrings = ResourceBundle.getBundle("javax.servlet.http.LocalStrings");

    private NoBodyOutputStream noBody;

    private PrintWriter writer;

	private boolean didSetContentLength;// 是否设置过内容长度
	private boolean usingOutputStream;// 表示是使用字节流还是字符流
 
	// *******************************************************************
    NoBodyResponse(HttpServletResponse r) {
		super(r);// 包裹了原本的响应
        noBody = new NoBodyOutputStream();
    }
	// *******************************************************************
	void setContentLength() {
		if (!didSetContentLength) {// 表示还没设置过内容长度
            if (writer != null) {
                writer.flush();
            }
            setContentLength(noBody.getContentLength());
        }
    }
	private void checkHeader(String name) {// 表示设置了内容长度域
		if ("content-length".equalsIgnoreCase(name)) {
			didSetContentLength = true;
		}
	}
	// *******************************************************************
	// 下面都是重写的方法
    @Override
    public void setContentLength(int len) {
        super.setContentLength(len);
        didSetContentLength = true;
    }
    @Override
    public void setContentLengthLong(long len) {
        super.setContentLengthLong(len);
        didSetContentLength = true;
    }
    @Override
    public void setHeader(String name, String value) {
        super.setHeader(name, value);
        checkHeader(name);
    }
    @Override
    public void addHeader(String name, String value) {
        super.addHeader(name, value);
        checkHeader(name);
    }
    @Override
    public void setIntHeader(String name, int value) {
        super.setIntHeader(name, value);
        checkHeader(name);
    }
    @Override
    public void addIntHeader(String name, int value) {
        super.addIntHeader(name, value);
        checkHeader(name);
    }
	// ***********************************************************************************
    @Override
	public ServletOutputStream getOutputStream() throws IOException {// 使用字节流

        if (writer != null) {
            throw new IllegalStateException(lStrings.getString("err.ise.getOutputStream"));
        }

        usingOutputStream = true;

        return noBody;
    }
    @Override
	public PrintWriter getWriter() throws UnsupportedEncodingException {// 使用字符流(底层还是上面的字节流)

        if (usingOutputStream) {
            throw new IllegalStateException(lStrings.getString("err.ise.getWriter"));
        }

        if (writer == null) {

            OutputStreamWriter w = new OutputStreamWriter(noBody, getCharacterEncoding());

            writer = new PrintWriter(w);
        }

        return writer;
    }
	// ***********************************************************************************
}
class NoBodyOutputStream extends ServletOutputStream {// 配合上面的响应头进行输出(字节流)

    private static final String LSTRING_FILE = "javax.servlet.http.LocalStrings";

    private static ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

	private int contentLength = 0;// 表示已经完成写的内容长度

	// *********************************************************************
	NoBodyOutputStream() {

	}
	// *********************************************************************
    int getContentLength() {
        return contentLength;
    }
	public boolean isReady() {
		return false;
	}
	public void setWriteListener(WriteListener writeListener) {

	}
	// *********************************************************************
    @Override
	public void write(int b) {// 写内容的时候不是真正的写
        contentLength++;
    }
    @Override
    public void write(byte buf[], int offset, int len) throws IOException {

        if (buf == null) {
            throw new NullPointerException(lStrings.getString("err.io.nullArray"));
        }

		if (offset < 0 || len < 0 || offset + len > buf.length) {// 越界了

            String msg = lStrings.getString("err.io.indexOutOfBounds");

            Object[] msgArgs = new Object[3];

            msgArgs[0] = Integer.valueOf(offset);
            msgArgs[1] = Integer.valueOf(len);
            msgArgs[2] = Integer.valueOf(buf.length);

            msg = MessageFormat.format(msg, msgArgs);

            throw new IndexOutOfBoundsException(msg);
        }

        contentLength += len;
    }
	// *********************************************************************
}
