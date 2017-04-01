package org.eclipse.jetty.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Scheduler;

public abstract class AbstractEndPoint extends IdleTimeout implements EndPoint {// 表示网络连接的一个端点

    private static final Logger LOG = Log.getLogger(AbstractEndPoint.class);

	private final long _created = System.currentTimeMillis();// 表示端点创建时间

	// 该端点关联的两端地址
	private final InetSocketAddress _local;// 本地地址
	private final InetSocketAddress _remote;// 远端地址

	private volatile Connection _connection;// 当基于该端点的连接创建完成时会设置端点的该成员(表示所在的连接)

	// **********************************************************
	private final FillInterest _fillInterest = new FillInterest() {
        @Override
		protected void needsFillInterest() throws IOException {//
			System.out.println("当前类为: " + this.getClass().getName());
			AbstractEndPoint.this.needsFillInterest();// SelectChannelEndPoint
        }
    };
	private final WriteFlusher _writeFlusher = new WriteFlusher(this) {// 用于处理写操作的FLUSH
        @Override
		protected void onIncompleteFlush() {
            AbstractEndPoint.this.onIncompleteFlush();
        }
    };
	protected abstract void onIncompleteFlush();// WRITEFLUSH完成
	protected abstract void needsFillInterest() throws IOException;// ????由底层实现
	public FillInterest getFillInterest() {
		return _fillInterest;
	}
	protected WriteFlusher getWriteFlusher() {
		return _writeFlusher;
	}

	// ***************************************************************************************************
	// 构造器
	protected AbstractEndPoint(Scheduler scheduler, InetSocketAddress local, InetSocketAddress remote) {
		super(scheduler);// 空闲超时
		_local = local;
		_remote = remote;
    }
	// ***************************************************************************************************
    @Override
	public InetSocketAddress getLocalAddress() {
        return _local;
    }
    @Override
	public InetSocketAddress getRemoteAddress() {
        return _remote;
    }
    @Override
	public long getCreatedTimeStamp() {
		return _created;
	}
	@Override
	public void close() {
		onClose();
	}
	@Override
	public void fillInterested(Callback callback) throws IllegalStateException {// 由外部调用(端点的接口)
		notIdle();// 刷新空闲计时时间
		_fillInterest.register(callback);// ReadCallback
	}
	@Override
	public boolean isFillInterested() {
		return _fillInterest.isInterested();
	}
	@Override
	public void write(Callback callback, ByteBuffer... buffers) throws IllegalStateException {
		_writeFlusher.write(callback, buffers);
	}
	@Override
	public Connection getConnection() {
        return _connection;
    }
    @Override
	public void setConnection(Connection connection) {
        _connection = connection;
    }
    @Override
	public void onOpen() {// 开始空闲超时计时
        super.onOpen();
    }
    @Override
	public void onClose() {
		super.onClose();// 空闲超时
        _writeFlusher.onClose();
        _fillInterest.onClose();
    }
	@Override
	public boolean isOptimizedForDirectBuffers() {
		return false;
	}
	@Override
	public void upgrade(Connection newConnection) {// 更新连接

		Connection old_connection = getConnection();

		ByteBuffer prefilled = (old_connection instanceof Connection.UpgradeFrom) ? ((Connection.UpgradeFrom) old_connection).onUpgradeFrom() : null;

		old_connection.onClose();
		old_connection.getEndPoint().setConnection(newConnection);

		if (newConnection instanceof Connection.UpgradeTo) {
			((Connection.UpgradeTo) newConnection).onUpgradeTo(prefilled);
		} else if (BufferUtil.hasContent(prefilled)) {// ????
			throw new IllegalStateException();
		}

		newConnection.onOpen();
	}
	// **********************************************************
    @Override
	protected void onIdleExpired(TimeoutException timeout) {

        Connection connection = _connection;

		if (connection != null && !connection.onIdleExpired()) {
			return;
		}

		boolean output_shutdown = isOutputShutdown();
		boolean input_shutdown = isInputShutdown();

        boolean fillFailed = _fillInterest.onFail(timeout);
        boolean writeFailed = _writeFlusher.onFail(timeout);

		if (isOpen() && (output_shutdown || input_shutdown) && !(fillFailed || writeFailed)) {
			close();
		} else {
			LOG.debug("ignored idle endpoint {}", this);
		}
    }

	// **********************************************************
    @Override
	public String toString() {

		Class<?> c = getClass();

		String name = c.getSimpleName();

		while (name.length() == 0 && c.getSuperclass() != null) {// 顶层父类
			c = c.getSuperclass();
			name = c.getSimpleName();
        }

        Connection connection = getConnection();

        return String.format("%s@%x{%s<->%d,%s,%s,%s,%s,%s,%d/%d,%s@%x}",
                name,
                hashCode(),
                getRemoteAddress(),
                getLocalAddress().getPort(),
				isOpen() ? "Open" : "CLOSED",
				isInputShutdown() ? "ISHUT" : "in",
				isOutputShutdown() ? "OSHUT" : "out",
                _fillInterest.toStateString(),
                _writeFlusher.toStateString(),
                getIdleFor(),
                getIdleTimeout(),
                connection == null ? null : connection.getClass().getSimpleName(),
                connection == null ? 0 : connection.hashCode());
    }
}
