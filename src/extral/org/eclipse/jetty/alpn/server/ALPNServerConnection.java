package extral.org.eclipse.jetty.alpn.server;

import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NegotiatingServerConnection;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import extral.org.eclipse.jetty.alpn.ALPN;

public class ALPNServerConnection extends NegotiatingServerConnection implements ALPN.ServerProvider {
    private static final Logger LOG = Log.getLogger(ALPNServerConnection.class);

	public ALPNServerConnection(Connector connector, EndPoint endPoint, SSLEngine engine, List<String> protocols,
			String defaultProtocol) {
        super(connector, endPoint, engine, protocols, defaultProtocol);
        ALPN.put(engine, this);
    }

    @Override
	public void unsupported() {
        select(Collections.<String>emptyList());
    }

    @Override
	public String select(List<String> clientProtocols) {
        SSLEngine sslEngine = getSSLEngine();
        List<String> serverProtocols = getProtocols();
        String tlsProtocol = sslEngine.getHandshakeSession().getProtocol();
        String tlsCipher = sslEngine.getHandshakeSession().getCipherSuite();
        String negotiated = null;

        // RFC 7301 states that the server picks the protocol
        // that it prefers that is also supported by the client.
		for (String serverProtocol : serverProtocols) {
			if (clientProtocols.contains(serverProtocol)) {
                ConnectionFactory factory = getConnector().getConnectionFactory(serverProtocol);
				if (factory instanceof CipherDiscriminator
						&& !((CipherDiscriminator) factory).isAcceptable(serverProtocol, tlsProtocol, tlsCipher)) {
                    continue;
                }

                negotiated = serverProtocol;
                break;
            }
        }
		if (negotiated == null) {
			if (clientProtocols.isEmpty()) {
                negotiated = getDefaultProtocol();
			} else {
                throw new IllegalStateException();
            }
        }

        setProtocol(negotiated);
        ALPN.remove(sslEngine);
        return negotiated;
    }

    @Override
	public void close() {
        ALPN.remove(getSSLEngine());
        super.close();
    }
}
