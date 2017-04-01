package javax.servlet;

import java.io.IOException;
import java.util.Enumeration;
import java.util.ResourceBundle;

public abstract class GenericServlet implements Servlet, ServletConfig, java.io.Serializable {// 最后接口是一个标志接口

	private static final long serialVersionUID = -8592279577370996712L;

	private static final String LSTRING_FILE = "javax.servlet.LocalStrings";
	
    private static ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

	private transient ServletConfig config;// 该SERVLET对应的配置

	//*************************************************************************
	public GenericServlet() {// 构造器(是对SERVLET的直接实现)

	}
	//*************************************************************************
	// SERVLET接口的完全实现
	public void init(ServletConfig config) throws ServletException {
		this.config = config;
		this.init();
    }
    public ServletConfig getServletConfig() {
		return config;
    }
	// 由底层实现
	public abstract void service(ServletRequest req, ServletResponse res) throws ServletException, IOException;
	public String getServletInfo() {
		return "";
    }
	public void destroy() {
    
	}
	//*************************************************************************
	public void init() throws ServletException {// ????

    }
	public void log(String msg) {
		getServletContext().log(getServletName() + ": " + msg);
	}
	public void log(String message, Throwable t) {
		getServletContext().log(getServletName() + ": " + message, t);
	}

	// ***************************************************************************
	// 对应接口的完全实现
	public ServletContext getServletContext() {// 通过配置获取上下文环境--用于某个SERVLET获取其所在的上下文环境
        ServletConfig sc = getServletConfig();
        if (sc == null) {
            throw new IllegalStateException(lStrings.getString("err.servlet_config_not_initialized"));
        }
        return sc.getServletContext();
    }
    public String getServletName() {
        ServletConfig sc = getServletConfig();
        if (sc == null) {
            throw new IllegalStateException(lStrings.getString("err.servlet_config_not_initialized"));
        }
        return sc.getServletName();
    }
    public String getInitParameter(String name) {
        ServletConfig sc = getServletConfig();
        if (sc == null) {
            throw new IllegalStateException(lStrings.getString("err.servlet_config_not_initialized"));
        }
        return sc.getInitParameter(name);
    }
    public Enumeration<String> getInitParameterNames() {
        ServletConfig sc = getServletConfig();
        if (sc == null) {
            throw new IllegalStateException(lStrings.getString("err.servlet_config_not_initialized"));
        }
        return sc.getInitParameterNames();
    }
}
