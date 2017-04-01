package org.eclipse.jetty.io;

import java.io.IOException;
import java.util.Map;

public interface ClientConnectionFactory {
    public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException;
}
