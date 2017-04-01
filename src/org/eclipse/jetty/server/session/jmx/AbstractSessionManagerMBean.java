package org.eclipse.jetty.server.session.jmx;

import org.eclipse.jetty.server.handler.AbstractHandlerContainer;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.jmx.AbstractHandlerMBean;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;

public class AbstractSessionManagerMBean extends AbstractHandlerMBean {
	public AbstractSessionManagerMBean(Object managedObject) {
        super(managedObject);
    }

    /* ------------------------------------------------------------ */
    @Override
	public String getObjectContextBasis()
    {
        if (_managed != null && _managed instanceof AbstractSessionManager)
        {
            AbstractSessionManager manager = (AbstractSessionManager)_managed;
            
            String basis = null;
            SessionHandler handler = manager.getSessionHandler();
            if (handler != null)
            {
                ContextHandler context = 
                    AbstractHandlerContainer.findContainerOf(handler.getServer(), 
                                                             ContextHandler.class,
                                                             handler);
                if (context != null)
                    basis = getContextName(context);
            }

            if (basis != null)
                return basis;
        }
        return super.getObjectContextBasis();
    }
}
