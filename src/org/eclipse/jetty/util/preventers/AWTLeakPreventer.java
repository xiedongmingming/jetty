package org.eclipse.jetty.util.preventers;

import java.awt.Toolkit;

public class AWTLeakPreventer extends AbstractLeakPreventer {

    @Override
	public void prevent(ClassLoader loader) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Pinning classloader for java.awt.EventQueue using " + loader);
		}
        Toolkit.getDefaultToolkit();
    }
}
