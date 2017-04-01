package javax.servlet;

import java.util.*;

public interface FilterRegistration extends Registration {

    public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames);
    public Collection<String> getServletNameMappings();
    public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns);
    public Collection<String> getUrlPatternMappings();

    interface Dynamic extends FilterRegistration, Registration.Dynamic {
    }
}

