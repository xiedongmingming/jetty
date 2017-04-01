package extral.org.eclipse.jetty.client.api;

import java.io.Closeable;

public interface Connection extends Closeable {//客户端的连接
    void send(Request request, Response.CompleteListener listener);
    @Override
    void close();
    boolean isClosed();
}
