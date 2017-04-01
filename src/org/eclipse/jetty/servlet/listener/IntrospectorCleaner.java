package org.eclipse.jetty.servlet.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class IntrospectorCleaner implements ServletContextListener {

    @Override
	public void contextInitialized(ServletContextEvent sce) {
        
    }
    @Override
	public void contextDestroyed(ServletContextEvent sce) {
        java.beans.Introspector.flushCaches();
    }

}
