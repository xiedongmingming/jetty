package org.eclipse.jetty.server;

import java.io.Closeable;
import java.io.IOException;

public interface NetworkConnector extends Connector, Closeable {//
    void open() throws IOException;
	@Override  void close();
	boolean isOpen();
    String getHost();
    int getPort();
    int getLocalPort();
}
