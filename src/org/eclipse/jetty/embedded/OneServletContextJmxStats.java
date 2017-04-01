package org.eclipse.jetty.embedded;

import java.lang.management.ManagementFactory;

import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.ConnectorStatistics;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class OneServletContextJmxStats {
    public static void main( String[] args ) throws Exception {
        Server server = new Server(8080);
        // Add JMX tracking to Server
        server.addBean(new MBeanContainer(ManagementFactory.getPlatformMBeanServer()));

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(DumpServlet.class, "/dump/*");
        context.addServlet(DefaultServlet.class, "/");

        // Add Connector Statistics tracking to all connectors
        ConnectorStatistics.addToAllConnectors(server);

        server.start();
        server.join();
    }
}
