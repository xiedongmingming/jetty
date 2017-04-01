package javax.servlet.http;

import java.util.EventListener;

public interface HttpSessionActivationListener extends EventListener { 
    public void sessionWillPassivate(HttpSessionEvent se); 
    public void sessionDidActivate(HttpSessionEvent se);
} 

