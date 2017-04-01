package extral.org.eclipse.jetty.alpn.server;

import java.util.List;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NegotiatingServerConnectionFactory;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import extral.org.eclipse.jetty.alpn.ALPN;

public class ALPNServerConnectionFactory extends NegotiatingServerConnectionFactory {
    private static final Logger LOG = Log.getLogger(ALPNServerConnectionFactory.class);

	public ALPNServerConnectionFactory(String protocols) {
        this(protocols.trim().split(",", 0));
    }

	public ALPNServerConnectionFactory(@Name("protocols") String... protocols) {
        super("alpn", protocols);
		try {
            ClassLoader alpnClassLoader = ALPN.class.getClassLoader();
			if (alpnClassLoader != null) {
                LOG.warn("ALPN must be in the boot classloader, not in: " + alpnClassLoader);
                throw new IllegalStateException("ALPN must be in the boot classloader");
            }
		} catch (Throwable x) {
            LOG.warn("ALPN not available", x);
            throw new IllegalStateException("ALPN not available", x);
        }
    }

    @Override
	protected AbstractConnection newServerConnection(Connector connector, EndPoint endPoint, SSLEngine engine,
			List<String> protocols, String defaultProtocol) {
        return new ALPNServerConnection(connector, endPoint, engine, protocols, defaultProtocol);
    }
}
