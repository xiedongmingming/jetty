package javax.servlet;

import java.util.Enumeration;

public interface ServletConfig {// SERVLET容器用于初始化SERVLET时用到的配置
	public String getServletName();// 获取该SERVLET的名称
	public ServletContext getServletContext();// 获取该SERVLET所在的上下文环境
	public String getInitParameter(String name);// 获取为该SERVLET配置的指定初始参数
	public Enumeration<String> getInitParameterNames();// 获取为该SERVLET配置的所有初始参数
}
