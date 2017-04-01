package org.eclipse.jetty.embedded;

import org.eclipse.jetty.server.Server;

public class SimplestServer {//the simplest possible jetty server.
    public static void main( String[] args ) throws Exception {
        Server server = new Server(8080);
        server.start();
        server.dumpStdErr();
        server.join();
    }
}
