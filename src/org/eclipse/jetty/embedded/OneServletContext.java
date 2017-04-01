package org.eclipse.jetty.embedded;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class OneServletContext {

    public static void main( String[] args ) throws Exception {

        Server server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);

        context.setContextPath("/");
        context.setResourceBase(System.getProperty("java.io.tmpdir"));

        server.setHandler(context);

        context.addServlet(DumpServlet.class, "/dump/*");//add dump servlet
        context.addServlet(DefaultServlet.class, "/");//add default servlet

        server.start();
        server.join();
    }
}
