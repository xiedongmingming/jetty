package org.eclipse.jetty.embedded;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;

public class MinimalServlets {

	public static void main(String[] args) throws Exception {

		// **********************************************************************
		// create a basic jetty server object that will listen on port 8080.
		// note that if you set this to port 0 then a randomly available port
        // will be assigned that you can either look in the logs for the port,
        // or programmatically obtain it for use in test cases.
		// *********************************************************************
        Server server = new Server(8080);

		// *********************************************************************
		// the servlethandler is a dead simple way to create a context handler
		// that is backed by an instance of a servlet. this handler then needs
		// to be registered with the server object.
		// *********************************************************************
        ServletHandler handler = new ServletHandler();

        server.setHandler(handler);

		// *********************************************************************
		// passing in the class for the servlet allows jetty to instantiate an
		// instance of that servlet and mount it on a given context path.
        // IMPORTANT:
		// this is a raw servlet, not a servlet that has been configured
		// through a web.xml @webservlet annotation, or anything similar.
		// *********************************************************************
        handler.addServletWithMapping(HelloServlet.class, "/*");

		server.start();// start things up!

		// *********************************************************************
		// the use of server.join() will make the current thread join and
        // wait until the server is done executing.
		// see
        // http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#join()
		// *********************************************************************
        server.join();
    }

    @SuppressWarnings("serial")
	public static class HelloServlet extends HttpServlet {

        @Override
		protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			super.doDelete(req, resp);
			System.out.println("doDelete");
		}

		@Override
		protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			super.doHead(req, resp);
			System.out.println("doHead");
		}

		@Override
		protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			super.doOptions(req, resp);
			System.out.println("doHead");
		}

		@Override
		protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			super.doPost(req, resp);
			System.out.println("doHead");
		}

		@Override
		protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			super.doPut(req, resp);
			System.out.println("doHead");
		}

		@Override
		protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			super.doTrace(req, resp);
			System.out.println("doHead");
		}

		@Override
		protected long getLastModified(HttpServletRequest req) {
			System.out.println("doHead");
			return super.getLastModified(req);
		}

		@Override
		public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
			super.service(req, res);
			System.out.println("doHead");
		}

		@Override
		protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			super.service(req, resp);
			System.out.println("doHead");
		}

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			super.doGet(request, response);
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("<h1>Hello from HelloServlet</h1>");
        }
    }
}
