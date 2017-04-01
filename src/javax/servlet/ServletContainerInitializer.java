package javax.servlet;

import java.util.Set;

public interface ServletContainerInitializer {
	public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException;
}
