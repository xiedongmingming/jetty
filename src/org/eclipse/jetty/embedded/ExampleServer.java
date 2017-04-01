package org.eclipse.jetty.embedded;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class ExampleServer {
    
	// SelectorProducer: 负责所有KEY的选择(在托管的选择器中--两个)
	//

	// 一个请求处理过程日志:

	// 要能接受一个WEB请求访问,首先要创建一个CONTEXTHANDLER,如下代码所示:
	public static void main1(String[] args) throws Exception {

		Server server = new Server(8080);

		ContextHandler context = new ContextHandler();

		context.setContextPath("/");
		context.setResourceBase(".");
		context.setClassLoader(Thread.currentThread().getContextClassLoader());

		server.setHandler(context);

		context.setHandler(new HelloHandler());

		server.start();
		server.join();
	}

	public static void main2(String[] args) throws Exception {

		Server server = new Server();

		HttpConfiguration httpConfig = new HttpConfiguration();// 配置HTTP

		httpConfig.setSendServerVersion(true);

		HttpConnectionFactory connectionFactory = new HttpConnectionFactory(httpConfig);

		ServerConnector connector = new ServerConnector(server, connectionFactory);

		connector.setPort(8080);

		server.setConnectors(new Connector[] { connector });

		ServletContextHandler root = new ServletContextHandler(null, "/", ServletContextHandler.SESSIONS);

		server.setHandler(root);

		root.addServlet(new ServletHolder(new org.eclipse.jetty.embedded.HelloServlet("Hello")), "/");

		server.start();
		server.join();
	}

	public static void main(String[] args) throws Exception {
        
		Server server = new Server();

		ServerConnector connector = new ServerConnector(server);
        
		connector.setPort(8080);
        
		server.setConnectors(new Connector[] { connector });

		ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(HelloServlet.class, "/hello");
		context.addServlet(HelloServlet2.class, "/hello2");
        context.addServlet(AsyncEchoServlet.class, "/echo/*");

		HandlerCollection handlers = new HandlerCollection();
		handlers.setHandlers(new Handler[] { context, new DefaultHandler() });

		server.setHandler(handlers);

		// org.eclipse.jetty.server.handler.HandlerCollection
		// 		org.eclipse.jetty.servlet.ServletContextHandler
		// 		org.eclipse.jetty.servlet.ServletHandler
		// 		org.eclipse.jetty.server.handler.DefaultHandler

		for (Handler handler : server.getHandlers()) {
			System.out.println(handler.getClass().getName());
			for (Handler handler1 : ((HandlerCollection) handler).getChildHandlers()) {
				System.out.println(handler1.getClass().getName());
			}
		}

		server.getURI();
		server.start();// 声明周期中的启动函数
        server.join();
    }
}
