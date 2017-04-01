package org.eclipse.jetty.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.ArrayUtil;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public abstract class AbstractConnectionFactory extends ContainerLifeCycle implements ConnectionFactory {//

	// 实现类列表:
	// HttpConnectionFactory
	// SslConnectionFactory

	private final String _protocol;// 支持的协议(第一个协议(首选协议))
	private final List<String> _protocols;// 该连接支持的协议集合(可以支持多个)

	private int _inputbufferSize = 8192;// 对应该连接的输入缓存大小

	// ******************************************************************************************************
	// 构造函数
	protected AbstractConnectionFactory(String protocol) {
		_protocol = protocol;// HttpVersion.HTTP_1_1.asString()、SSL
		_protocols = Collections.unmodifiableList(Arrays.asList(new String[] { protocol }));
    }
	protected AbstractConnectionFactory(String... protocols) {
		_protocol = protocols[0];
		_protocols = Collections.unmodifiableList(Arrays.asList(protocols));
    }
	// ******************************************************************************************************
    @Override
	public String getProtocol() {
        return _protocol;
    }
    @Override
	public List<String> getProtocols() {
        return _protocols;
    }
	// ******************************************************************************************************
	public int getInputBufferSize() {
        return _inputbufferSize;
    }
	public void setInputBufferSize(int size) {
		_inputbufferSize = size;
    }
	protected AbstractConnection configure(AbstractConnection connection, Connector connector, EndPoint endPoint) {// 对一个连接进行配置

		// 第一个参数表示由子类生成的具体连接类: HttpConnection
		// 第二个参数表示连接器
		// 第三个参数表示端点

		connection.setInputBufferSize(getInputBufferSize());
        
		if (connector instanceof ContainerLifeCycle) {// 添加监听器

			ContainerLifeCycle aggregate = (ContainerLifeCycle) connector;

			for (Connection.Listener listener : aggregate.getBeans(Connection.Listener.class)) {// 将连接器中的监听器都加入到该连接上
				connection.addListener(listener);
			}
        }

		for (Connection.Listener listener : getBeans(Connection.Listener.class)) {// 将该连接工厂的监听器也加入到该连接上
			connection.addListener(listener);
		}

        return connection;
    }
	public static ConnectionFactory[] getFactories(SslContextFactory sslContextFactory, ConnectionFactory... factories) {// 根据参数生成新的连接

		factories = ArrayUtil.removeNulls(factories);

		if (sslContextFactory == null) {
			return factories;
		}

		for (ConnectionFactory factory : factories) {

			if (factory instanceof HttpConfiguration.ConnectionFactory) {// 如果有配置则添加定制器

				HttpConfiguration config = ((HttpConfiguration.ConnectionFactory) factory).getHttpConfiguration();

				if (config.getCustomizer(SecureRequestCustomizer.class) == null) {
					config.addCustomizer(new SecureRequestCustomizer());
				}
            }
        }

		return ArrayUtil.prependToArray(new SslConnectionFactory(sslContextFactory, factories[0].getProtocol()), factories, ConnectionFactory.class);
    }
	// ******************************************************************************************************
	@Override
	public String toString() {
		return String.format("%s@%x%s", this.getClass().getSimpleName(), hashCode(), getProtocols());
	}
	// ******************************************************************************************************
}
