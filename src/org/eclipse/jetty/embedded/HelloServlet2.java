package org.eclipse.jetty.embedded;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class HelloServlet2 extends HttpServlet {

	final String greeting;

	public HelloServlet2() {
        this("Hello");
    }
	public HelloServlet2(String greeting) {
        this.greeting = greeting;
    }

    @Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		System.out.println("测试是否是单利类2: " + this.getServletContext().getClass().hashCode());

		System.out.println("测试是否是单利类: " + this.hashCode());
		System.out.println("测试是否是单利类: " + this.getServletConfig().getClass().getCanonicalName());
		System.out.println("测试是否是单利类: " + this.getServletConfig().getServletContext().getClass().getCanonicalName());
    	
		// System.out.println(request.getHeader("Content-Length"));
		// System.out.println(request.getContentLength());

		// System.out.println("进行重定向..........");
		// response.sendRedirect("http://baidu.com/ss?dddd=fadfasd");
		// response.setStatus(HttpServletResponse.SC_OK);
		// return;

		// System.out.println("ceshi: " + request.getServerName());// localhost
		// System.out.println("ceshi: " + request.getScheme());// http
		// System.out.println("ceshi: " + request.getServletPath());// /hello
		// System.out.println("ceshi: " + request.getServerPort());// 8080
		//
		// System.out.println("测试: " + request.getClass().getName());
		// System.out.println("测试: " + response.getClass().getName());
		// // 测试: org.eclipse.jetty.server.Request
		// // 测试: org.eclipse.jetty.server.Response
		//
		// System.out.println("测试: " +
		// request.getServletContext().getClass().getName());
		// // JETTY实现的SERVLET上下文:
		// // ContextHandler.Context
		// // StaticContext
		// // StaticContext extends AttributesMap implements ServletContext
		//
		// // System.out.println("测试:
		// "+request.getCookies()[0].getClass().getName());
		// // System.out.println("测试:
		// "+request.getSession().getClass().getName());
		// // 测试: org.eclipse.jetty.servlet.ServletContextHandler$Context
		//
		// System.out.println("测试: " + request.getAuthType());
		// System.out.println("测试: " + request.changeSessionId());
		// System.out.println("测试: " + request.getCharacterEncoding());
		// System.out.println("测试: " + request.getContentLength());
		// System.out.println("测试: " + request.getContentLengthLong());
		// System.out.println("测试: " + request.getContentType());
		// System.out.println("测试: " + request.getContextPath());
		//
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().println("<h1>" + greeting + " from HelloServlet</h1>");// 响应的发出过程
    }

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doGet(req, resp);
	}

}
