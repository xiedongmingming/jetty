package javax.servlet;

import java.io.IOException;

public interface Servlet {// 接口(共五个接口函数)--由容器来管理(为单利类)

	// 涉及到的内容有:
	// ServletConfig
	// ServletRequest
	// ServletResponse
	// ServletException

	public void init(ServletConfig config) throws ServletException;// 用于初始化一个SERVLET
	public ServletConfig getServletConfig();// 用于获取该SERVLET对应的配置(初始参数等)
	public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException;// 处理请求
	public String getServletInfo();//
	public void destroy();// 销毁该SERVELT
}
