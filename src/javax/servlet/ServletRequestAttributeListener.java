package javax.servlet;

import java.util.EventListener;

public interface ServletRequestAttributeListener extends EventListener {
	public void attributeAdded(ServletRequestAttributeEvent srae);
	public void attributeRemoved(ServletRequestAttributeEvent srae);
	public void attributeReplaced(ServletRequestAttributeEvent srae);
}

