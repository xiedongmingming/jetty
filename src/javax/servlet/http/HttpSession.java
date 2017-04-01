package javax.servlet.http;

import java.util.Enumeration;

import javax.servlet.ServletContext;

public interface HttpSession {// 表示一个HTTP会话

	public long getCreationTime();// 表示该回话创建的时间
    public String getId();
    public long getLastAccessedTime();
    public ServletContext getServletContext();
    public void setMaxInactiveInterval(int interval);
    public int getMaxInactiveInterval();
    public Object getAttribute(String name);
    public Enumeration<String> getAttributeNames();
    public void setAttribute(String name, Object value);
	public void removeAttribute(String name);
	public void invalidate();
	public boolean isNew();

    @Deprecated
	public void putValue(String name, Object value);
    @Deprecated
	public void removeValue(String name);
	@Deprecated
	public String[] getValueNames();
	@Deprecated
	public Object getValue(String name);
	@Deprecated
	public HttpSessionContext getSessionContext();
}

