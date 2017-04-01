package org.eclipse.jetty.embedded;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;

public class OneContext {
	public static void main(String[] args) throws Exception {

		Server server = new Server(8080);

		// add a single handler on context "/hello"
        ContextHandler context = new ContextHandler();
        context.setContextPath( "/hello" );
        context.setHandler( new HelloHandler() );

		// can be accessed using http://localhost:8080/hello
		server.setHandler(context);

		server.start();// start the server
        server.join();
    }
}
