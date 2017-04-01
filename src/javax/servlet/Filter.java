package javax.servlet;

import java.io.IOException;

public interface Filter {// 过滤器(用于实现对SERVLET的请求进行过滤)

	// 涉及到的东西包括:
	// FilterConfig
	// FilterChain

	// 表示初始化该过滤器
    public void init(FilterConfig filterConfig) throws ServletException;

	// 执行过滤操作
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException;

	// 销毁该过滤器
    public void destroy();
}

