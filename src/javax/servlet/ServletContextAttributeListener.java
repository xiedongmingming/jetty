package javax.servlet;

import java.util.EventListener;

public interface ServletContextAttributeListener extends EventListener {

    public void attributeAdded(ServletContextAttributeEvent event);
    public void attributeRemoved(ServletContextAttributeEvent event);
    public void attributeReplaced(ServletContextAttributeEvent event);
}

