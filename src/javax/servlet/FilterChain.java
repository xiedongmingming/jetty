package javax.servlet;

import java.io.IOException;

public interface FilterChain {// 过滤链(执行下面的方法表示继续过滤链的执行)
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException;
}

