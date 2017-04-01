package org.eclipse.jetty.server;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class SslConnectionFactory extends AbstractConnectionFactory {

	private final SslContextFactory _sslContextFactory;// 作为BEAN

	private final String _nextProtocol;// HttpVersion.HTTP_1_1.asString()

	// *****************************************************************************************************************
    public SslConnectionFactory() {
        this(HttpVersion.HTTP_1_1.asString());
    }
    public SslConnectionFactory(@Name("next") String nextProtocol) {
		this(null, nextProtocol);
    }
    public SslConnectionFactory(@Name("sslContextFactory") SslContextFactory factory, @Name("next") String nextProtocol) {

		super("SSL");

		_sslContextFactory = factory == null ? new SslContextFactory() : factory;

        _nextProtocol = nextProtocol;

        addBean(_sslContextFactory);
    }
	// *****************************************************************************************************************
    public SslContextFactory getSslContextFactory() {
        return _sslContextFactory;
    }
	// *****************************************************************************************************************
    @Override
    protected void doStart() throws Exception {

        super.doStart();

        SSLEngine engine = _sslContextFactory.newSSLEngine();

        engine.setUseClientMode(false);

        SSLSession session = engine.getSession();

        if (session.getPacketBufferSize() > getInputBufferSize()) {
            setInputBufferSize(session.getPacketBufferSize());
		}
    }
    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint) {
        SSLEngine engine = _sslContextFactory.newSSLEngine(endPoint.getRemoteAddress());
        engine.setUseClientMode(false);
        SslConnection sslConnection = newSslConnection(connector, endPoint, engine);
        sslConnection.setRenegotiationAllowed(_sslContextFactory.isRenegotiationAllowed());
        configure(sslConnection, connector, endPoint);
        ConnectionFactory next = connector.getConnectionFactory(_nextProtocol);
        EndPoint decryptedEndPoint = sslConnection.getDecryptedEndPoint();
        Connection connection = next.newConnection(connector, decryptedEndPoint);
        decryptedEndPoint.setConnection(connection);
        return sslConnection;
    }
    protected SslConnection newSslConnection(Connector connector, EndPoint endPoint, SSLEngine engine) {
        return new SslConnection(connector.getByteBufferPool(), connector.getExecutor(), endPoint, engine);
    }

	// *****************************************************************************************************************
    @Override
    public String toString() {
		return String.format("%s@%x{%s->%s}",
				this.getClass().getSimpleName(),
				hashCode(),
				getProtocol(),
				_nextProtocol);
    }
	// *****************************************************************************************************************
}
