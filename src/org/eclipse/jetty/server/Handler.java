package org.eclipse.jetty.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.component.Destroyable;
import org.eclipse.jetty.util.component.LifeCycle;

public interface Handler extends LifeCycle, Destroyable {// 接口--也是一个生命周期实现

	// 用于对请求进行处理:
	// 第一个参数表示请求的目标路径
	// 第二个参数表示请求(底层类)
	// 第三个参数表示请求(抽象类)
	// 第四个参数表示响应(抽象类)
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

	public void setServer(Server server);// 处理器所在的服务器
	public Server getServer();// 获取服务器

	// **********************************************************
    @Override
    public void destroy();
}

