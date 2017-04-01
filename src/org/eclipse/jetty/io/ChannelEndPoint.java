package org.eclipse.jetty.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Scheduler;

public class ChannelEndPoint extends AbstractEndPoint {

    private static final Logger LOG = Log.getLogger(ChannelEndPoint.class);

	private final SocketChannel _channel;// 端点所在的通道
	private final Socket _socket;// 通道对应的套接字(由上面通道获取)

	private volatile boolean _ishut;// 输入关闭--逻辑标志(最终要看关联的通道或对应的SOCKET)
	private volatile boolean _oshut;// 输出关闭

	// ***************************************************************************
	public ChannelEndPoint(Scheduler scheduler, SocketChannel channel) {// 
		super(scheduler, (InetSocketAddress) channel.socket().getLocalSocketAddress(), (InetSocketAddress) channel.socket().getRemoteSocketAddress());
		_channel = channel;//
		_socket = channel.socket();
    }
	// ***************************************************************************
	protected void shutdownInput() {
		_ishut = true;
		if (_oshut) {// 都关闭了
			close();
		}
	}
	public ByteChannel getChannel() {
		return _channel;
	}
	public Socket getSocket() {
		return _socket;
	}
	// ***************************************************************************
    @Override
    public boolean isOptimizedForDirectBuffers() {
        return true;
    }
    @Override
	public boolean isOpen() {// 关联的通道是否开启
        return _channel.isOpen();
    }
    @Override
    public void shutdownOutput() {
        _oshut = true;
		if (_channel.isOpen()) {// 关掉对应SOCKET的输出
			try {
				if (!_socket.isOutputShutdown()) {
					_socket.shutdownOutput();
				}
			} catch (IOException e) {
                LOG.debug(e);
			} finally {
				if (_ishut) {
                    close();
                }
            }
        }
    }
    @Override
	public boolean isOutputShutdown() {
        return _oshut || !_channel.isOpen() || _socket.isOutputShutdown();
    }
    @Override
	public boolean isInputShutdown() {
        return _ishut || !_channel.isOpen() || _socket.isInputShutdown();
    }
    @Override
	public void close() {// 关闭整个端点
        super.close();
		try {
            _channel.close();
		} catch (IOException e) {
            LOG.debug(e);
		} finally {
			_ishut = true;
			_oshut = true;
        }
    }
    @Override
	public int fill(ByteBuffer buffer) throws IOException {// 表示将通道中的数据读取到参数BUFFER中

		System.out.println("HTTP生成过程: 4. ChannelEndPoint");

		if (_ishut) {
			return -1;
		}
		int pos = BufferUtil.flipToFill(buffer);//
		try {
			int filled = _channel.read(buffer);// java.nio.HeapByteBuffer
			if (filled > 0) {
				notIdle();
			} else if (filled == -1) {
				shutdownInput();
			}
            return filled;
		} catch (IOException e) {
            LOG.debug(e);
            shutdownInput();
            return -1;
		} finally {
			BufferUtil.flipToFlush(buffer, pos);
        }
    }
    @Override
	public boolean flush(ByteBuffer... buffers) throws IOException {// 表示将BUFFER中的数据发送到通道上
		long flushed = 0;
		try {
			if (buffers.length == 1) {
				flushed = _channel.write(buffers[0]);
			} else if (buffers.length > 1) {
				flushed = _channel.write(buffers, 0, buffers.length);
			} else {
				for (ByteBuffer b : buffers) {
					if (b.hasRemaining()) {
						int l = _channel.write(b);
						if (l > 0) {
							flushed += l;
						}
						if (b.hasRemaining()) {
							break;
						}
                    }
                }
            }
		} catch (IOException e) {
            throw new EofException(e);
        }
		if (flushed > 0) {
			notIdle();
		}
		for (ByteBuffer b : buffers) {
			if (!BufferUtil.isEmpty(b)) {//
				return false;
			}
		}
        return true;
    }
    @Override
	public Object getTransport() {
        return _channel;
    }
    @Override
	protected void onIncompleteFlush() {
        throw new UnsupportedOperationException();
    }
    @Override
	protected void needsFillInterest() throws IOException {
        throw new UnsupportedOperationException();
    }
}
