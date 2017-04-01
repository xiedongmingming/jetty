package org.eclipse.jetty.embedded.test;

import java.util.Set;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;

public class HttpConnectionFactoryCarryingBidRequestPaths extends HttpConnectionFactory {// HTTP连接生成器

	// private MetricRegistry metrics = Metrics.getInstance();

	@SuppressWarnings("unused")
	private Set<String> bidRequestServletPaths = null;// ????

	// private final Timer stats = metrics.timer("connections");

    public HttpConnectionFactoryCarryingBidRequestPaths(HttpConfiguration httpConfig) {
        super(httpConfig);
    }

	// @Override
	// public org.eclipse.jetty.io.Connection newConnection(Connector connector,
	// EndPoint endPoint) {
	// Connection c = configure(
	// new YciHttpConnection(getHttpConfiguration(), connector, endPoint,
	// bidRequestServletPaths), connector,
	// endPoint);
	// c.addListener(new Connection.Listener() {
	// // private Timer.Context context;
	// @Override
	// public void onOpened(Connection connection) {
	// // this.context = stats.time();
	// }
	// @Override
	// public void onClosed(Connection connection) {
	// // context.stop();
	// }
	// });
	// return c;
	// }
	//
	// public void setBidRequestServletPaths(Set<String> bidRequestServletPaths)
	// {
	// this.bidRequestServletPaths = bidRequestServletPaths;
	// }
}
