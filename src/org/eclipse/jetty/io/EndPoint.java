package org.eclipse.jetty.io;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;

import org.eclipse.jetty.util.Callback;

public interface EndPoint extends Closeable {// 接口(共22个接口)

	InetSocketAddress getLocalAddress();
	InetSocketAddress getRemoteAddress();

    boolean isOpen();

    long getCreatedTimeStamp();

    void shutdownOutput();

    boolean isOutputShutdown();
    boolean isInputShutdown();

	@Override
	void close();// 表示关闭整个端点

	// 下面两个函数完成端点数据的读写操作
	int fill(ByteBuffer buffer) throws IOException;
    boolean flush(ByteBuffer... buffer) throws IOException;

	Object getTransport();// SocketChannel

    long getIdleTimeout();
    void setIdleTimeout(long idleTimeout);

	void fillInterested(Callback callback) throws ReadPendingException;// 填充该端点接下来事件的回调处理器

	boolean isFillInterested();// 该端点接下来事件的回调处理器是否填充了

	// 写操作的具体实现
	void write(Callback callback, ByteBuffer... buffers) throws WritePendingException;// 通过该端口向外发送数据

    Connection getConnection();
    void setConnection(Connection connection);

	void onOpen();
	void onClose();

	boolean isOptimizedForDirectBuffers();// ????

	public void upgrade(Connection newConnection);// 更新端点所在的连接
}
