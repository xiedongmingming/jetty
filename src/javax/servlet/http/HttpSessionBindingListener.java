package javax.servlet.http;

import java.util.EventListener;

public interface HttpSessionBindingListener extends EventListener {
    public void valueBound(HttpSessionBindingEvent event);
    public void valueUnbound(HttpSessionBindingEvent event);
}

