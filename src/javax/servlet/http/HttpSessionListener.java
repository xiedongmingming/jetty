package javax.servlet.http;

import java.util.EventListener;

public interface HttpSessionListener extends EventListener {
    public void sessionCreated(HttpSessionEvent se);
    public void sessionDestroyed(HttpSessionEvent se);
}

