package org.eclipse.jetty.io;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Scheduler;

public class NetworkTrafficSelectChannelEndPoint extends SelectChannelEndPoint {

	// 端点的最低层实现:没有必须要实现的接口函数

	// 在父类的基础上添加监听器

    private static final Logger LOG = Log.getLogger(NetworkTrafficSelectChannelEndPoint.class);

	private final List<NetworkTrafficListener> listeners;// 所包含的所有监听器

	// *******************************************************************************
	public NetworkTrafficSelectChannelEndPoint(SocketChannel channel,
			ManagedSelector selectSet,
			SelectionKey key,
			Scheduler scheduler,
			long idleTimeout,
			List<NetworkTrafficListener> listeners) throws IOException {

        super(channel, selectSet, key, scheduler, idleTimeout);

        this.listeners = listeners;
    }
	// *******************************************************************************
	// 重写父类的方法--用于在适当的位置回调监听器
    @Override
	public int fill(ByteBuffer buffer) throws IOException {// 表示将网络上接收到的数据读取到该参数缓存中

		// 参数为NIO缓存

		int read = super.fill(buffer);

        notifyIncoming(buffer, read);

        return read;
    }
    @Override
	public boolean flush(ByteBuffer... buffers) throws IOException {// 表示将参数中的数据发送到网络上

		boolean flushed = true;

		for (ByteBuffer b : buffers) {

			if (b.hasRemaining()) {

				int position = b.position();

				ByteBuffer view = b.slice();

				flushed &= super.flush(b);

				int l = b.position() - position;

				view.limit(view.position() + l);

                notifyOutgoing(view);

				if (!flushed) {
					break;
				}
            }
        }
        return flushed;
    }
    @Override
	public void onOpen() {
        super.onOpen();
		if (listeners != null && !listeners.isEmpty()) {
			for (NetworkTrafficListener listener : listeners) {
				try {
                    listener.opened(getSocket());
				} catch (Exception x) {
                    LOG.warn(x);
                }
            }
        }
    }
    @Override
	public void onClose() {// 表示关闭该网络连接
        super.onClose();
		if (listeners != null && !listeners.isEmpty()) {
			for (NetworkTrafficListener listener : listeners) {
				try {
                    listener.closed(getSocket());
				} catch (Exception x) {
                    LOG.warn(x);
                }
            }
        }
    }
	// *******************************************************************************
	public void notifyIncoming(ByteBuffer buffer, int read) {

		// 第一个参数表示存放网络上数据的缓存
		// 第二个参数表示从网络上读取到上述缓存中数据个数

		if (listeners != null && !listeners.isEmpty() && read > 0) {
			for (NetworkTrafficListener listener : listeners) {
				try {
					ByteBuffer view = buffer.asReadOnlyBuffer();// 生成一个视图数据
					listener.incoming(getSocket(), view);// 调用父类的函数
				} catch (Exception x) {
                    LOG.warn(x);
                }
            }
        }
    }
	public void notifyOutgoing(ByteBuffer view) {

		// 第一个参数表示待发送数据的一个视图

		if (listeners != null && !listeners.isEmpty() && view.hasRemaining()) {
			Socket socket = getSocket();
			for (NetworkTrafficListener listener : listeners) {
				try {
                    listener.outgoing(socket, view);   
				} catch (Exception x) {
                    LOG.warn(x);
                }
            }
        }
    }
	// *******************************************************************************
}
