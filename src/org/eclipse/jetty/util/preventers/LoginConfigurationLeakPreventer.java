package org.eclipse.jetty.util.preventers;

public class LoginConfigurationLeakPreventer extends AbstractLeakPreventer {

    @Override
	public void prevent(ClassLoader loader) {
		try {
            Class.forName("javax.security.auth.login.Configuration", true, loader);
		} catch (ClassNotFoundException e) {
            LOG.warn(e);
        }
    }
}
