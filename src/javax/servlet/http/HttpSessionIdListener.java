package javax.servlet.http;

import java.util.EventListener;

public interface HttpSessionIdListener extends EventListener {
    public void sessionIdChanged(HttpSessionEvent event, String oldSessionId);
}
