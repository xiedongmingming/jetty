package javax.servlet.http;

import java.util.EventListener;

public interface HttpSessionAttributeListener extends EventListener {
    public void attributeAdded(HttpSessionBindingEvent event);
    public void attributeRemoved(HttpSessionBindingEvent event);
    public void attributeReplaced(HttpSessionBindingEvent event);
}

