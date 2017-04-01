package org.eclipse.jetty.embedded;

import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;

public class OneHandler {

    public static void main( String[] args ) throws Exception {

        Server server = new Server(8080);

        server.getConnectors()[0].getConnectionFactory(HttpConnectionFactory.class).setHttpCompliance(HttpCompliance.LEGACY);

        server.setHandler(new HelloHandler());

        server.start();
        server.join();
    }
}
