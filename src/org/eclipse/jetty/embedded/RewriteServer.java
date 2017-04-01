package org.eclipse.jetty.embedded;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import extral.org.eclipse.jetty.rewrite.RewriteCustomizer;
import extral.org.eclipse.jetty.rewrite.handler.CompactPathRule;
import extral.org.eclipse.jetty.rewrite.handler.RewriteRegexRule;

public class RewriteServer {// 表示地址重写

	private static final Logger LOG = LoggerFactory.getLogger(RewriteServer.class);

	// HTTP的处理过程:
	// ExecuteProduceConsume.executeProduceConsume-->task.run();
	// CreateEndPoint.run-->createEndPoint(channel, key);
	// ManagedSelector.createEndPoint-->_selectorManager.newConnection(channel, endPoint, selectionKey.attachment());
	// ServerConnector.ServerConnectorManager-->newConnection
	// HttpConnectionFactory.newConnection-->new HttpConnection
	// HttpConnection-->newHttpChannel
	// HttpConnection-->HttpChannelOverHttp
	// HttpChannelOverHttp-->HttpChannel
	// Request
    
	public static void main(String[] args) throws Exception {
		
        Server server = new Server(8080);

		// 获取第一个连接器的连接工厂使用的连接配置
		HttpConfiguration config = server.getConnectors()[0].getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();

		for (Connector connector : server.getConnectors()) {// 本机就一个连接器: ServerConnector
			LOG.info("连接器: " + connector.getClass().getCanonicalName());
		}

		RewriteCustomizer rewrite = new RewriteCustomizer();// 定制化配置

		config.addCustomizer(rewrite);

		rewrite.addRule(new CompactPathRule());
		rewrite.addRule(new RewriteRegexRule("(.*)foo(.*)", "$1FOO$2"));// 进行URL地址的替换
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(DumpServlet.class, "/*");

        server.start();
        server.join();

		LOG.info("");
    }
}
