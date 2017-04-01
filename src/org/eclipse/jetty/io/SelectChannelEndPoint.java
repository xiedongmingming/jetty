package org.eclipse.jetty.io;

import java.io.Closeable;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Locker;
import org.eclipse.jetty.util.thread.Scheduler;

public class SelectChannelEndPoint extends ChannelEndPoint implements ManagedSelector.SelectableEndPoint {// 关联的选择器和其管理器
    
	// 端点的低层实现:没有必须要实现的接口函数

	public static final Logger LOG = Log.getLogger(SelectChannelEndPoint.class);

	private final Locker _locker = new Locker();// ???

	private boolean _updatePending;// 当前是否处于更新挂起状态(当通道产生了事件需要处理时此时不能执行更新)

	private final AtomicBoolean _open = new AtomicBoolean();// 标识该通道是否打开了

	private final ManagedSelector _selector;// 被管理的选择器--表示该通道由该选择器管理

	private final SelectionKey _key;// 该通道关联的KEY(用于读取选中的KEY或设置新关注的KEY)

	// 下面这两个值用于进行事件切换
	private int _currentInterestOps;// 表示当前关注的事件
	private int _desiredInterestOps;// 表示期望的事件(可能与上面的事件不同步)

	private final Runnable _runUpdateKey = new Runnable() {// 用于KEY更新
        @Override
        public void run() {
            updateKey();
        }
        @Override
        public String toString() {
			return SelectChannelEndPoint.this.toString() + ": runupdatekey";
        }
    };

	// ****************************************************************************
	private abstract class RunnableCloseable implements Runnable, Closeable {// 抽象类
        @Override
        public void close() {
            try {
                SelectChannelEndPoint.this.close();
            } catch (Throwable x) {
                LOG.warn(x);
            }
        }
    }
	private final Runnable _runFillable = new RunnableCloseable() {// 针对读--当通道选中读事件时会运行该任务
        @Override
        public void run() {
			System.out.println("a、请求处理过程");
			getFillInterest().fillable();//
        }
        @Override
        public String toString() {
			return SelectChannelEndPoint.this.toString() + ": runfillable";
        }
    };
	private final Runnable _runCompleteWrite = new RunnableCloseable() {// 针对写--当通道选中写事件时会运行该任务
        @Override
        public void run() {
            getWriteFlusher().completeWrite();
        }
        @Override
        public String toString() {
			return SelectChannelEndPoint.this.toString() + ": runcompletewrite";
        }
    };
	private final Runnable _runCompleteWriteFillable = new RunnableCloseable() {// 同时针对读写--当通道同时选中读写事件时会运行该任务
        @Override
        public void run() {
            getWriteFlusher().completeWrite();
            getFillInterest().fillable();
        }
        @Override
        public String toString() {
			return SelectChannelEndPoint.this.toString() + ": runfillablecompletewrite";
        }
    };

	// ***************************************************************************
	private void changeInterests(int operation) {// (在原有的事件上加上参数中指定的事件)变换之后提交到执行器上进行执行

		int oldInterestOps;
		int newInterestOps;

		boolean pending;

		try (Locker.Lock lock = _locker.lock()) {

			pending = _updatePending;// ????

			oldInterestOps = _desiredInterestOps;
			newInterestOps = oldInterestOps | operation;

			if (newInterestOps != oldInterestOps) {
				_desiredInterestOps = newInterestOps;
			}
		}

		LOG.info("changeInterests p={} {}->{} for {}", pending, oldInterestOps, newInterestOps, this);

		if (!pending) {// 表示没有挂起
			_selector.submit(_runUpdateKey);// 执行任务--由选择器负责异步执行
		}
	}

	// ***************************************************************************
	// 构造函数
    public SelectChannelEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey key, Scheduler scheduler, long idleTimeout) {
    	
        super(scheduler, channel);
        
        _selector = selector;
        _key = key;
        
        setIdleTimeout(idleTimeout);
    }

	// *******************************************************************************************************
	// 下面是重写的父类方法
	// 感兴趣的事件
    @Override
    protected void needsFillInterest() {
        changeInterests(SelectionKey.OP_READ);
    }
    @Override
    protected void onIncompleteFlush() {
        changeInterests(SelectionKey.OP_WRITE);
    }
    @Override
	public Runnable onSelected() {// 在哪调用???表示该通道被选中--若是异步则返回对应任务否则直接执行任务(目前都采用异步处理)

		// 调用地方: Runnable task = ((SelectableEndPoint) attachment).onSelected();

		// 一次HTTP请求对应一次READ事件???

		int readyOps = _key.readyOps();// 1--表示读 4--表示写

		System.out.println("一次HTTP请求产生的选择事件: " + readyOps + " : " + System.currentTimeMillis());

        int oldInterestOps;
        int newInterestOps;

        try (Locker.Lock lock = _locker.lock()) {

			_updatePending = true;// 不要在进行KEY的更新了

            oldInterestOps = _desiredInterestOps;
			newInterestOps = oldInterestOps & ~readyOps;// 清除已经触发的事件

            _desiredInterestOps = newInterestOps;
        }

		boolean readable = (readyOps & SelectionKey.OP_READ) != 0;// 本次产生的是读事件?
		boolean writable = (readyOps & SelectionKey.OP_WRITE) != 0;// 本次产生的是写事件?


		LOG.info("onselected {}->{} r={} w={} for {}", oldInterestOps, newInterestOps, readable, writable, this);

		// ********************************************************************
		if (readable && getFillInterest().isCallbackNonBlocking()) {// 表示是读选中--并且是非阻塞式
			System.out.println("表示通道有读取事件: 进行读取操作");
			_runFillable.run();// 直接执行--SelectChannelEndPoint
            readable = false;
        }
		if (writable && getWriteFlusher().isCallbackNonBlocking()) {// 表示是写选中--并且是非阻塞式
			_runCompleteWrite.run();// 直接执行
            writable = false;
        }
		// ********************************************************************

        Runnable task = readable ? (writable ? _runCompleteWriteFillable : _runFillable) : (writable ? _runCompleteWrite : null);

		System.out.println("返回的任务为: " + task.getClass().getName());

        return task;
    }
    @Override
	public void updateKey() {// 更新KEY
        try {

            int oldInterestOps;
            int newInterestOps;

            try (Locker.Lock lock = _locker.lock()) {

				_updatePending = false;// 表示清空PENDING

                oldInterestOps = _currentInterestOps;
                newInterestOps = _desiredInterestOps;

                if (oldInterestOps != newInterestOps) {
                    _currentInterestOps = newInterestOps;
					_key.interestOps(newInterestOps);// 注册关注的事件
                }
            }

			LOG.info("key interests updated {} -> {} on {}", oldInterestOps, newInterestOps, this);

        } catch (CancelledKeyException x) {
			LOG.debug("ignoring key update for concurrently closed channel {}", this);
            close();
        } catch (Throwable x) {
			LOG.warn("ignoring key update for " + this, x);
            close();
        }
    }
    @Override
    public void close() {
        if (_open.compareAndSet(true, false)) {
			super.close();// 关闭整个端点
			_selector.destroyEndPoint(this);// 由选择器负责异步关闭端点连接
        }
    }
    @Override
    public boolean isOpen() {
        return _open.get();
    }
    @Override
    public void onOpen() {
		if (_open.compareAndSet(false, true)) {// 开启空闲超时计时
            super.onOpen();
		}
    }
    @Override
    public String toString() {

		try {

            boolean valid = _key != null && _key.isValid();

            int keyInterests = valid ? _key.interestOps() : -1;
            int keyReadiness = valid ? _key.readyOps() : -1;

            return String.format("%s{io=%d/%d, kio=%d, kro=%d}", super.toString(), _currentInterestOps, _desiredInterestOps, keyInterests, keyReadiness);

        } catch (Throwable x) {
            return String.format("%s{io=%s, kio=-2, kro=-2}", super.toString(), _desiredInterestOps);
        }
    }
	// *******************************************************************************************************
}
