package org.eclipse.jetty.embedded.test;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class ExampleServer {
    
	public static void main( String[] args ) throws Exception {
        
		Server server = new Server();

		//*************************************************************************************************
		//construct a serverconnector with a private instance of httpconnectionfactory as the only factory.
		//*************************************************************************************************
		ServerConnector connector = new ServerConnector(server);
        
		connector.setPort(8080);
        
		server.setConnectors(new Connector[] { connector });

		ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
		context.addServlet(HelloServlet.class, "/seyren");
        context.addServlet(AsyncEchoServlet.class, "/echo/*");

		HandlerCollection handlers = new HandlerCollection();
		handlers.setHandlers(new Handler[] { context, new DefaultHandler() });
		server.setHandler(handlers);
		server.getURI();
        server.start();
        server.join();
    }
}
