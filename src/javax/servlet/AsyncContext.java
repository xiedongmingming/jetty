package javax.servlet;

public interface AsyncContext {// 该对象代表异步处理的上下文--它提供了一些工具方法,可完成设置异步调用的超时时长,DISPATCH用于请求、启动后台线程、获取REQUEST、RESPONSE对象等功能

    static final String ASYNC_REQUEST_URI = "javax.servlet.async.request_uri";
    static final String ASYNC_CONTEXT_PATH = "javax.servlet.async.context_path";
    static final String ASYNC_PATH_INFO = "javax.servlet.async.path_info";
    static final String ASYNC_SERVLET_PATH = "javax.servlet.async.servlet_path";
    static final String ASYNC_QUERY_STRING = "javax.servlet.async.query_string";

    public ServletRequest getRequest();
    public ServletResponse getResponse();

    public boolean hasOriginalRequestAndResponse();

    public void dispatch();
    public void dispatch(String path);
    public void dispatch(ServletContext context, String path);

    public void complete();

	public void start(Runnable run);// 启动异步调用的线程

    public void addListener(AsyncListener listener);
    public void addListener(AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse);
    public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException; 

	public void setTimeout(long timeout);// 设置异步调用的超时时长
    public long getTimeout();
}
