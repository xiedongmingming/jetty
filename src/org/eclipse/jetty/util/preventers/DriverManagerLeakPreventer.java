package org.eclipse.jetty.util.preventers;

import java.sql.DriverManager;

public class DriverManagerLeakPreventer extends AbstractLeakPreventer {
    @Override
	public void prevent(ClassLoader loader) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Pinning DriverManager classloader with " + loader);
		}
        DriverManager.getDrivers();
    }
}
