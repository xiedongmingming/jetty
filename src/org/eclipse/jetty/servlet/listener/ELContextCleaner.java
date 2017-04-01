package org.eclipse.jetty.servlet.listener;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ELContextCleaner implements ServletContextListener {

    private static final Logger LOG = Log.getLogger(ELContextCleaner.class);

    @Override
	public void contextInitialized(ServletContextEvent sce) {
    }

    @Override
	public void contextDestroyed(ServletContextEvent sce) {
		try {
            Class<?> beanELResolver = Loader.loadClass(this.getClass(), "javax.el.BeanELResolver");
            Field field = getField(beanELResolver);
            purgeEntries(field);
		} catch (ClassNotFoundException e) {

		} catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
            LOG.warn("Cannot purge classes from javax.el.BeanELResolver", e);
		} catch (NoSuchFieldException e) {
            LOG.debug("Not cleaning cached beans: no such field javax.el.BeanELResolver.properties");
        }

    }


	protected Field getField(Class<?> beanELResolver) throws SecurityException, NoSuchFieldException {
		if (beanELResolver == null) {
			return null;
		}
        return beanELResolver.getDeclaredField("properties");
    }

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void purgeEntries(Field properties) throws IllegalArgumentException, IllegalAccessException {

		if (properties == null) {
			return;
		}

		if (!properties.isAccessible()) {
			properties.setAccessible(true);
		}

        Map map = (Map) properties.get(null);
		if (map == null) {
			return;
		}

        Iterator<Class<?>> itor = map.keySet().iterator();
		while (itor.hasNext()) {
            Class<?> clazz = itor.next();
			if (Thread.currentThread().getContextClassLoader().equals(clazz.getClassLoader())) {
                itor.remove();
			} else {

            }
        }
    }
}
