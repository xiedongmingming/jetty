package org.eclipse.jetty.io;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadPendingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class FillInterest {

	// 表示一种感兴趣的事件(包装一个回调函数:当该事件发生时执行回调并将该回调清除)

    private final static Logger LOG = Log.getLogger(FillInterest.class);

	private final AtomicReference<Callback> _interested = new AtomicReference<>(null);// 引用注册的回调函数(只有一个)

	private Throwable _lastSet;// 表示上一次注册时间(调试时使用)

	// *********************************************************************
	protected FillInterest() {
    }

	// *********************************************************************
	public void register(Callback callback) throws ReadPendingException {// 打开连接时会注册回调函数
		if (callback == null) {
			throw new IllegalArgumentException();
		}
		if (_interested.compareAndSet(null, callback)) {// 之前的事件必须被处理掉了
			if (LOG.isDebugEnabled()) {
				LOG.debug("{} register {}", this, callback);
				_lastSet = new Throwable(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + ":" + Thread.currentThread().getName());
            }
		} else {// 表示不能变更(已经设置的还未处理完成)
			LOG.warn("read pending for {} prevented {}", _interested, callback);
			if (LOG.isDebugEnabled()) {
				LOG.warn("callback set at ", _lastSet);
			}
            throw new ReadPendingException();
        }
		try {// 表示注册成功了
			needsFillInterest();// 由底层实现
		} catch (Throwable e) {
            onFail(e);
        }
    }
	abstract protected void needsFillInterest() throws IOException;// 注册成功时调用
	public boolean onFail(Throwable cause) {// 表示上面抽象函数执行失败时的回调
		Callback callback = _interested.get();
		if (callback != null && _interested.compareAndSet(callback, null)) {// 清除之前的设置
			callback.failed(cause);// 回调错误处理函数
			return true;
		}
		return false;
	}
	// *********************************************************************
	public void fillable() {// 回调成功并清除
		System.out.println("b、请求处理过程");
        Callback callback = _interested.get();
		if (callback != null && _interested.compareAndSet(callback, null)) {
			callback.succeeded();
		}
    }
	public boolean isInterested() {// 是否有事件注册
        return _interested.get() != null;
    }
	public boolean isCallbackNonBlocking() {
        Callback callback = _interested.get();
		return callback != null && callback.isNonBlocking();
    }
	public void onClose() {
        Callback callback = _interested.get();
		if (callback != null && _interested.compareAndSet(callback, null)) {
			callback.failed(new ClosedChannelException());// 关闭
		}
    }

	// *********************************************************************
    @Override
	public String toString() {
		return String.format("FillInterest@%x{%b,%s}", hashCode(), _interested.get() != null, _interested.get());
    }
	public String toStateString() {
		return _interested.get() == null ? "-" : "FI";
    }
}
