package javax.servlet;

import java.util.Collection;
import java.util.Set;

public interface ServletRegistration extends Registration {

    public Set<String> addMapping(String... urlPatterns);
    public Collection<String> getMappings();
    public String getRunAsRole();

    interface Dynamic extends ServletRegistration, Registration.Dynamic {
        public void setLoadOnStartup(int loadOnStartup);
        public Set<String> setServletSecurity(ServletSecurityElement constraint);

		public void setMultipartConfig(MultipartConfigElement multipartConfig);
        public void setRunAsRole(String roleName);
    }
}

