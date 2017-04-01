package org.eclipse.jetty.util.thread;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Locker {
	
	private static final boolean SPIN = Boolean.getBoolean(Locker.class.getName() + ".spin");

    private final boolean _spin;
	
	private final ReentrantLock _lock = new ReentrantLock();// 閲嶅叆閿�
	
    private final AtomicReference<Thread> _spinLockState = new AtomicReference<>(null);//
	
	private final Lock _unlock = new Lock();// 鍐呴儴绫�

	//****************************************************
    public Locker() {
        this(SPIN);
    }
    public Locker(boolean spin) {
        this._spin = spin;
    }
	//****************************************************
	public Lock lock() {// 閿佸畾 -- 涓ょ瀹炵幇
        if (_spin) {
            spinLock();
        } else {
            concLock();
		}
        return _unlock;
    }

	private void spinLock() {// 妯℃嫙閲嶅叆閿� --
		Thread current = Thread.currentThread();// 璋冪敤閿佸畾鏃剁殑绾跨▼
        while (true) {//using test-and-test-and-set for better performance.
			Thread locker = _spinLockState.get();// 涓婁竴娆￠攣瀹氭椂鐨勭嚎绋�
			if (locker != null || !_spinLockState.compareAndSet(null, current)) {// 绗簩涓潯浠惰〃绀哄厛姣旇緝鐒跺悗璁剧疆(姣旇緝缁撴灉杩斿洖)
				if (locker == current) {// 琛ㄧず涓婁竴娆′篃鏄绾跨▼
                    throw new IllegalStateException("Locker is not reentrant");
				}
                continue;
            }
            return;
        }
    }
    private void concLock() {
        if (_lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Locker is not reentrant");
		}
        _lock.lock();
    }
    public boolean isLocked() {
        if (_spin) {
            return _spinLockState.get() != null;
        } else {
            return _lock.isLocked();
		}
    }
    public class Lock implements AutoCloseable {
        @Override
        public void close() {
            if (_spin) {
				_spinLockState.set(null);// 璁剧疆涓虹┖ -- 鍘熷瓙寮曠敤
            } else {
				_lock.unlock();// 閲嶅叆閿佽В閿�
			}
        }
    }
}
