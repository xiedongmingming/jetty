package org.eclipse.jetty.server;

import java.net.Socket;

import org.eclipse.jetty.io.ChannelEndPoint;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.Connection.Listener;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.SslConnection.DecryptedEndPoint;

public class SocketCustomizationListener implements Listener {
    private final boolean _ssl;
    
    public SocketCustomizationListener() {
        this(true);
    }
    public SocketCustomizationListener(boolean ssl) {
        _ssl = ssl;
    }
    @Override
    public void onOpened(Connection connection) {
        EndPoint endp = connection.getEndPoint();
        boolean ssl = false;
        if (_ssl && endp instanceof DecryptedEndPoint) {
            endp = ((DecryptedEndPoint)endp).getSslConnection().getEndPoint();
            ssl = true;
        }
        if (endp instanceof ChannelEndPoint) {
            Socket socket = ((ChannelEndPoint)endp).getSocket();
            customize(socket, connection.getClass(), ssl);
        }
    }
    protected void customize(Socket socket, Class<? extends Connection> connection, boolean ssl) {
    }
    @Override
    public void onClosed(Connection connection) {
    }
}
