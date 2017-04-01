package javax.servlet;

import java.util.EventListener;

public interface ServletRequestListener extends EventListener {
    public void requestDestroyed(ServletRequestEvent sre);
    public void requestInitialized(ServletRequestEvent sre);
}
