package org.eclipse.jetty.embedded;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class DumpServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        
		System.out.println("测试: DumpServlet");

    	response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);

        PrintWriter out = response.getWriter();

        out.println("<h1>DumpServlet</h1>");
        out.println("<pre>");
        out.println("requestURI=" + request.getRequestURI());
        out.println("contextPath=" + request.getContextPath());
        out.println("servletPath=" + request.getServletPath());
        out.println("pathInfo=" + request.getPathInfo());
        out.println("session=" + request.getSession(true).getId());

        String r = request.getParameter("resource");
        if (r != null) {
            out.println("resource(" + r + ")=" + getServletContext().getResource(r));
        }

        out.println("</pre>");
    }
}
