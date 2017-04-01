package org.eclipse.jetty.io;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class AbstractConnection implements Connection {
	
    private static final Logger LOG = Log.getLogger(AbstractConnection.class);

	private final List<Listener> listeners = new CopyOnWriteArrayList<>();// 存放该连接的所有监听器

	private final long _created = System.currentTimeMillis();// 表示该连接的创建时间

	private final EndPoint _endPoint;// 该连接关联的端点--SelectChannelEndPoint

	private final Executor _executor;// 共用连接器的执行器--QueuedThreadPool

	private final Callback _readCallback;// 固定为读回调--ReadCallback

	private int _inputBufferSize = 2048;// 表示该连接的输入缓存大小(HttpConfiguration)

	// *************************************************************************
	protected AbstractConnection(EndPoint endp, Executor executor) {// 构造函数

        if (executor == null) {
			throw new IllegalArgumentException("executor must not be null!");
		}

		_endPoint = endp;// SelectChannelEndPoint
		_executor = executor;// QueuedThreadPool

		_readCallback = new ReadCallback();//
    }
	// *************************************************************************
	// 实现的接口函数
    @Override
	public void addListener(Listener listener) {// 为该连接添加监听器(一般会将所有的连接器上的监听器都添加到该连接上、外加连接工厂上的监听器)
        listeners.add(listener);
    }
    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
	@Override
	public void onOpen() {// 调用监听器的回调函数
		for (Listener listener : listeners) {
			listener.onOpened(this);
		}
	}
	@Override
	public void onClose() {// 调用监听器的回调函数
		for (Listener listener : listeners) {
			listener.onClosed(this);
		}
	}
	@Override
	public EndPoint getEndPoint() {
		return _endPoint;
	}
	@Override
	public boolean onIdleExpired() {// ???
		return true;
	}
	@Override
	public int getMessagesIn() {//
		return -1;
	}
	@Override
	public int getMessagesOut() {//
		return -1;
	}
	@Override
	public long getBytesIn() {//
		return -1;
	}
	@Override
	public long getBytesOut() {//
		return -1;
	}
	@Override
	public long getCreatedTimeStamp() {
		return _created;
	}
	@Override
	public void close() {// 主要是关闭端点
		getEndPoint().close();
	}
	// *************************************************************************
    public int getInputBufferSize() {
        return _inputBufferSize;
    }
	public void setInputBufferSize(int inputBufferSize) {// 设置输入缓存大小
        _inputBufferSize = inputBufferSize;
    }
    protected Executor getExecutor() {
        return _executor;
    }
	@Deprecated
	public boolean isDispatchIO() {
        return false;
    }
	protected void failedCallback(final Callback callback, final Throwable x) {// 什么时候调用????

		// 第一个参数表示针对该连接失败的回调函数
		// 第二个参数表示该连接失败的原因

		if (callback.isNonBlocking()) {// 表示回调函数是非阻塞的
			try {
                callback.failed(x);
			} catch (Exception e) {
                LOG.warn(e);
            }
		} else {// 表示该回调函数是阻塞的(执行异步处理)
			try {
				getExecutor().execute(new Runnable() {
                    @Override
					public void run() {
						try {
							callback.failed(x);
						} catch (Exception e) {
                            LOG.warn(e);
                        }
                    }
                });
			} catch (RejectedExecutionException e) {
                LOG.debug(e);
				callback.failed(x);// 同步阻塞执行
            }
        }
    }

	// ***************************************************************
	//
	public void fillInterested() {// 打开连接时调用(将对应的回调函数添加到端点上)
		getEndPoint().fillInterested(_readCallback);// 填充该端点接下来事件的回调处理器
    }
	public boolean isFillInterested() {
        return getEndPoint().isFillInterested();
    }
	// ***************************************************************
	private class ReadCallback implements Callback {// 表示该连接的都回调函数
        @Override
        public void succeeded() {
			onFillable();// 表示可以读取请求数据了--由实现类提供
        }
        @Override
        public void failed(final Throwable x) {
			onFillInterestedFailed(x);// 提供默认实现
        }
        @Override
        public String toString() {
			return String.format("AC.ReadCB@%x{%s}", AbstractConnection.this.hashCode(), AbstractConnection.this);
        }
    }
	// 连接读回调函数中调用的函数
	public abstract void onFillable();// 抽象函数

	protected void onFillInterestedFailed(Throwable cause) {//
		if (_endPoint.isOpen()) {// 端点必须打开
			boolean close = true;
			if (cause instanceof TimeoutException) {// 表示是读超时所致
				close = onReadTimeout();// 可以被底层覆盖用于是否在读超时时执行关闭连接操作
			}
			if (close) {
				if (_endPoint.isOutputShutdown()) {// 表示输出已经被关闭
					_endPoint.close();// 关闭整个端点
				} else {// 否则关闭输出
					_endPoint.shutdownOutput();
					fillInterested();// 继续关注
				}
			}
		}
	}
	protected boolean onReadTimeout() {
		return true;
	}
	// ***************************************************************

	@Override
	public String toString() {
		return String.format("%s@%x[%s]", getClass().getSimpleName(), hashCode(), _endPoint);
	}
}