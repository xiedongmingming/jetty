package org.eclipse.jetty.io;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Scheduler;

public abstract class IdleTimeout {

	// 为每一个端点提供空闲超时控制

	@SuppressWarnings("unused")
	private static final Logger LOG = Log.getLogger(IdleTimeout.class);

	private final Scheduler _scheduler;// 空闲超时调度器(为连接器上的调度器)(ScheduledExecutorScheduler)

	private final AtomicReference<Scheduler.Task> _timeout = new AtomicReference<>();// 表示引用空闲超时调度器中的任务

	private volatile long _idleTimeout;// 空闲超时时间--继承了连接器上的空闲超时时间

	private volatile long _idleTimestamp = System.currentTimeMillis();// 上一次空闲开始的时间(需要不停的更新)

	private final Runnable _idleTask = new Runnable() {// 执行空闲超时任务
        @Override
		public void run() {
			long idleLeft = checkIdleTimeout();// 表示剩余的时间
			if (idleLeft >= 0) {// 表示还有剩余时间(小于0表示还未开始计时)
				scheduleIdleTimeout(idleLeft > 0 ? idleLeft : getIdleTimeout());
			}
        }
    };
	// **********************************************************
	public IdleTimeout(Scheduler scheduler) {
        _scheduler = scheduler;
    }
	// **********************************************************
	public Scheduler getScheduler() {
        return _scheduler;
    }
	public long getIdleTimestamp() {
        return _idleTimestamp;
    }
	public long getIdleFor() {// 表示已经空闲了多长时间
        return System.currentTimeMillis() - getIdleTimestamp();
    }
	public long getIdleTimeout() {
        return _idleTimeout;
    }
	public void setIdleTimeout(long idleTimeout) {// 设置超时时间
        long old = _idleTimeout;

        _idleTimeout = idleTimeout;
		if (old > 0) {
			if (old <= idleTimeout) {
				return;
			}
			deactivate();
        }
		if (isOpen()) {
			activate();
		}
    }
	public void notIdle() {
        _idleTimestamp = System.currentTimeMillis();
    }
	private void scheduleIdleTimeout(long delay) {//
        Scheduler.Task newTimeout = null;
		if (isOpen() && delay > 0 && _scheduler != null) {
			newTimeout = _scheduler.schedule(_idleTask, delay, TimeUnit.MILLISECONDS);
		}
        Scheduler.Task oldTimeout = _timeout.getAndSet(newTimeout);
		if (oldTimeout != null) {// 取消原来的任务
			oldTimeout.cancel();
		}
    }
	public void onOpen() {// 会被回调的--开始计时
        activate();
    }
	private void activate() {// 开始时手动触发执行
		if (_idleTimeout > 0) {
			_idleTask.run();
		}
    }
	public void onClose() {
        deactivate();
    }
	private void deactivate() {
		Scheduler.Task oldTimeout = _timeout.getAndSet(null);// 清空原有值
		if (oldTimeout != null) {
			oldTimeout.cancel();// 取消任务
		}
    }
	protected long checkIdleTimeout() {

		if (isOpen()) {// 必须开始计时

            long idleTimestamp = getIdleTimestamp();
            long idleTimeout = getIdleTimeout();
            long idleElapsed = System.currentTimeMillis() - idleTimestamp;

            long idleLeft = idleTimeout - idleElapsed;

			if (idleTimestamp != 0 && idleTimeout > 0) {//
				if (idleLeft <= 0) {// 表示超时了
					try {
						// 调用底层实现函数进行逻辑处理
						onIdleExpired(new TimeoutException("idle timeout expired: " + idleElapsed + "/" + idleTimeout + " ms"));
					} finally {
						notIdle();// 更新计时时间
                    }
                }
            }
            return idleLeft >= 0 ? idleLeft : 0;
        }
        return -1;
    }
	// **********************************************************
	protected abstract void onIdleExpired(TimeoutException timeout);// 表示空闲超时--底层实现
	public abstract boolean isOpen();// 表示是否开启空闲超时计时--底层实现
	// **********************************************************
}
