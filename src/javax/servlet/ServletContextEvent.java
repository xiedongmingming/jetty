package javax.servlet;

public class ServletContextEvent extends java.util.EventObject {// 表示一个事件

    private static final long serialVersionUID = -7501701636134222423L;

	public ServletContextEvent(ServletContext source) {// 参数表示事件源
        super(source);
    }

	// *********************************************************
	public ServletContext getServletContext() {// 获取事件源
        return (ServletContext) super.getSource();
    }
}
