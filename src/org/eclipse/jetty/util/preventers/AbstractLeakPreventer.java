package org.eclipse.jetty.util.preventers;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class AbstractLeakPreventer extends AbstractLifeCycle {
    
	protected static final Logger LOG = Log.getLogger(AbstractLeakPreventer.class);

    abstract public void prevent(ClassLoader loader);
    
    @Override
	protected void doStart() throws Exception {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();// ????
		try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
			prevent(getClass().getClassLoader());// 调用底层实现
            super.doStart();
		} finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
    }
}
