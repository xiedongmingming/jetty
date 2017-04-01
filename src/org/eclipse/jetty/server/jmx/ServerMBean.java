package org.eclipse.jetty.server.jmx;

import org.eclipse.jetty.jmx.ObjectMBean;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;

@ManagedObject("MBean Wrapper for Server")
public class ServerMBean extends ObjectMBean
{
    private final long startupTime;
    private final Server server;

    public ServerMBean(Object managedObject)
    {
        super(managedObject);
        startupTime = System.currentTimeMillis();
        server = (Server)managedObject;
    }

    @ManagedAttribute("contexts on this server")
    public Handler[] getContexts()
    {
        return server.getChildHandlersByClass(ContextHandler.class);
    }

    @ManagedAttribute("the startup time since January 1st, 1970 (in ms)")
    public long getStartupTime()
    {
        return startupTime;
    }
}
