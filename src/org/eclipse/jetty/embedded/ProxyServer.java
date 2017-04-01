package org.eclipse.jetty.embedded;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import extral.org.eclipse.jetty.proxy.ConnectHandler;
import extral.org.eclipse.jetty.proxy.ProxyServlet;

public class ProxyServer {

    public static void main( String[] args ) throws Exception {

        Server server = new Server();
       
        ServerConnector connector = new ServerConnector(server);
        
        connector.setPort(8888);
        
        server.addConnector(connector);
        
		// ************************************************
		// setup proxy handler to handle coonect methods
		// ************************************************
		ConnectHandler proxy = new ConnectHandler();
       
		server.setHandler(proxy);

		// ************************************************
		// setup proxy servlet
		// ************************************************
        ServletContextHandler context = new ServletContextHandler(proxy, "/", ServletContextHandler.SESSIONS);

        ServletHolder proxyServlet = new ServletHolder(ProxyServlet.class);

        proxyServlet.setInitParameter("blackList", "www.eclipse.org");

        context.addServlet(proxyServlet, "/*");

        server.start();
    }
}
