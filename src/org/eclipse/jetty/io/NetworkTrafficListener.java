package org.eclipse.jetty.io;

import java.net.Socket;
import java.nio.ByteBuffer;

public interface NetworkTrafficListener {// 表示网络流量监听器

	public void opened(Socket socket);// 参数为对应的SOCKET
	public void closed(Socket socket);

    public void incoming(Socket socket, ByteBuffer bytes);
    public void outgoing(Socket socket, ByteBuffer bytes);

	public static class Adapter implements NetworkTrafficListener {
        @Override
		public void opened(Socket socket) {

        }
        @Override
		public void closed(Socket socket) {

        }
        @Override
		public void incoming(Socket socket, ByteBuffer bytes) {

        }
        @Override
		public void outgoing(Socket socket, ByteBuffer bytes) {

        }
    }
}
