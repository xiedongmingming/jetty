package org.eclipse.jetty.embedded;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;

public class JarServer {
	
    public static void main(String[] args) throws Exception {
        
    	Server server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler();
        
        Resource.setDefaultUseCaches(true);

		Resource base = Resource.newResource("jar:file:src/content.jar!/");
        
        context.setBaseResource(base);
        context.addServlet(new ServletHolder(new DefaultServlet()), "/");
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] {context, new DefaultHandler()});
        server.setHandler(handlers);

        server.start();
        server.join();
    }
}
