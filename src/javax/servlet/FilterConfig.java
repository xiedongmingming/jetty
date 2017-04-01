package javax.servlet;

import java.util.Enumeration;

public interface FilterConfig {// 用于配置过滤器

    public String getFilterName();
    public ServletContext getServletContext();
    public String getInitParameter(String name);
    public Enumeration<String> getInitParameterNames();

}
