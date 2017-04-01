package org.eclipse.jetty.embedded.test;

import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnection;

public class YciHttpConnection extends HttpConnection {

	public YciHttpConnection(HttpConfiguration config, Connector connector, EndPoint endPoint,
			HttpCompliance compliance, boolean recordComplianceViolations) {
		super(config, connector, endPoint, compliance, recordComplianceViolations);
		// TODO Auto-generated constructor stub
	}

	// private String savedPathInfo = null;
	// private Set<String> bidRequestServletPaths = null;
	//
	// public YciHttpConnection(HttpConfiguration config, Connector connector,
	// EndPoint endPoint,
	// Set<String> bidRequestServletPaths) {
	// // super(config, connector, endPoint);
	// this.bidRequestServletPaths = bidRequestServletPaths;
	// }
	//
	// protected void setPathInfo(String pathInfo) {
	// savedPathInfo = pathInfo;
	// }
	//
	// @Override
	// protected HttpChannelOverHttp newHttpChannel(HttpInput<ByteBuffer>
	// httpInput) {
	// return new HttpChannelOverHttp(getConnector(), getHttpConfiguration(),
	// getEndPoint(), this, httpInput) {
	// @Override
	// public boolean startRequest(HttpMethod httpMethod, String method,
	// ByteBuffer uri, HttpVersion version) {
	// getRequest().setAttribute("jettyStartTime",
	// ((YciSelectChannelEndPoint) getEndPoint()).getLastSelected());
	// boolean requestStarted = super.startRequest(httpMethod, method, uri,
	// version);
	// String pathInfo = getRequest().getPathInfo();
	// if (pathInfo != null) {
	// pathInfo = pathInfo.substring(1);
	// }
	// setPathInfo(pathInfo);
	// return requestStarted;
	// }
	// };
	// }
	//
	// private boolean isBidRequestServletPath() {
	// return (bidRequestServletPaths != null &&
	// bidRequestServletPaths.contains(savedPathInfo));
	// }
	//
	// @Override
	// public void onFillable() {
	// super.onFillable();
	// if (isBidRequestServletPath()) {
	// long onSelectedTime = ((YciSelectChannelEndPoint)
	// getEndPoint()).getLastSelected();
	// // TODO
	// }
	// }
	//
	// public static String getConnectionId() {
	// HttpConnection currentConnection = HttpConnection.getCurrentConnection();
	// if (currentConnection == null) {
	// return null;
	// }
	// StringBuilder s = new StringBuilder();
	// s.append(currentConnection.getEndPoint().getRemoteAddress().getAddress().getHostAddress()).append(',')
	// .append(currentConnection.getEndPoint().getRemoteAddress().getPort()).append(',')
	// .append(currentConnection.getCreatedTimeStamp());
	// return s.toString();
	// }
}
