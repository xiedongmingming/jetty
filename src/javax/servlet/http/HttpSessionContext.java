package javax.servlet.http;

import java.util.Enumeration;

public interface HttpSessionContext {
    public HttpSession getSession(String sessionId);
    public Enumeration<String> getIds();
}