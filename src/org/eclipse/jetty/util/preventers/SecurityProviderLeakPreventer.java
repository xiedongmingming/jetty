package org.eclipse.jetty.util.preventers;

import java.security.Security;

public class SecurityProviderLeakPreventer extends AbstractLeakPreventer {
    @Override
	public void prevent(ClassLoader loader) {
        Security.getProviders();
    }
}
