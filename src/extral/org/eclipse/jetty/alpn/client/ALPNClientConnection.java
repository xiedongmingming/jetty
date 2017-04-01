package extral.org.eclipse.jetty.alpn.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.NegotiatingClientConnection;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import extral.org.eclipse.jetty.alpn.ALPN;


public class ALPNClientConnection extends NegotiatingClientConnection implements ALPN.ClientProvider {

	private static final Logger LOG = Log.getLogger(ALPNClientConnection.class);

    private final List<String> protocols;

	public ALPNClientConnection(EndPoint endPoint, Executor executor, ClientConnectionFactory connectionFactory,
			SSLEngine sslEngine, Map<String, Object> context, List<String> protocols) {
        super(endPoint, executor, sslEngine, connectionFactory, context);
        this.protocols = protocols;
        ALPN.put(sslEngine, this);
    }

    @Override
	public void unsupported() {
        ALPN.remove(getSSLEngine());
        completed();
    }

    @Override
	public List<String> protocols() {
        return protocols;
    }

    @Override
	public void selected(String protocol) {
		if (protocols.contains(protocol)) {
            ALPN.remove(getSSLEngine());
            completed();
		} else {
            LOG.info("Could not negotiate protocol: server [{}] - client {}", protocol, protocols);
            close();
        }
    }

    @Override
	public void close() {
        ALPN.remove(getSSLEngine());
        super.close();
    }

	@Override
	public boolean supports() {
		// TODO Auto-generated method stub
		return false;
	}

}
