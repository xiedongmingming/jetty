package org.eclipse.jetty.util.component;

import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.util.Uptime;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class AbstractLifeCycle implements LifeCycle {// 唯一直接实现的函数
	
	// 1.0.5.0 -- 服务继承体系中的最终类
	// 2.0.4.0 -- 该构造器回去调用父类的构造器
    
	private static final Logger LOG = Log.getLogger(AbstractLifeCycle.class);// 最先被初始化
    
	// 服务器运行的状态 -- 六种状态
    public static final String STARTING = "STARTING";
    public static final String STARTED = "STARTED";
	public static final String RUNNING = "RUNNING";
    public static final String STOPPING = "STOPPING";
	public static final String STOPPED = "STOPPED";
	public static final String FAILED = "FAILED";


	//**************************************
	// 写时复制:用于写入时的并发安全
	//**************************************
	private final CopyOnWriteArrayList<LifeCycle.Listener> _listeners = new CopyOnWriteArrayList<LifeCycle.Listener>();// 所有对该生命周期产生的事件进行监听的监听器
    
	private final Object _lock = new Object();// 同步用的锁(启动停止时用到)
	
	private final int __FAILED = -1;
	private final int __STOPPED = 0;// 初始状态
	private final int __STARTING = 1;
	private final int __STARTED = 2;
	private final int __STOPPING = 3;

	private volatile int _state = __STOPPED;// 生命周期的状态(在该类下记录)--对应上面的状态值
    


	// **********************************************************************
	protected void doStart() throws Exception {// 执行启动过程--调用具体的实现函数

	}
	protected void doStop() throws Exception {// 执行停止过程--调用具体的实现函数

	}
	// **********************************************************************
	// 实现的抽象函数: AbstractLifeCycle
    @Override
	public final void start() throws Exception {// 服务器启动的入口函数--各种生命周期组件启动时的接口(在内部会调用具体组件的启动实现)
        synchronized (_lock) {
            try {
                if (_state == __STARTED || _state == __STARTING) {
					return;
				}
				setStarting();// 修改状态并回调监听器函数
				doStart();// 靠底层继承类实现--(最底层的)
				setStarted();// 修改状态并回调监听器函数
            } catch (Throwable e) {
                setFailed(e);
                throw e;
            }
        }
    }
    @Override
    public final void stop() throws Exception {
        synchronized (_lock) {
            try {
                if (_state == __STOPPING || _state == __STOPPED) {
					return;
				}
                setStopping();
                doStop();
                setStopped();
            } catch (Throwable e) {
                setFailed(e);
                throw e;
            }
        }
    }
    @Override
    public boolean isRunning() {
		final int state = _state;// 为了防止变动????
        return state == __STARTED || state == __STARTING;
    }
    @Override
    public boolean isStarted() {
        return _state == __STARTED;
    }
    @Override
    public boolean isStarting() {
        return _state == __STARTING;
    }
    @Override
    public boolean isStopping() {
        return _state == __STOPPING;
    }
    @Override
    public boolean isStopped() {
        return _state == __STOPPED;
    }
    @Override
    public boolean isFailed() {
        return _state == __FAILED;
    }
    @Override
    public void addLifeCycleListener(LifeCycle.Listener listener) {
        _listeners.add(listener);
    }
    @Override
    public void removeLifeCycleListener(LifeCycle.Listener listener) {
        _listeners.remove(listener);
    }
	//**********************************************************************
    public String getState() {
        switch(_state) {
            case __FAILED: 
				return FAILED;
            case __STARTING: 
				return STARTING;
            case __STARTED: 
				return STARTED;
            case __STOPPING: 
				return STOPPING;
            case __STOPPED: 
				return STOPPED;
        }
        return null;
    }

	public static String getState(LifeCycle lc) {// 静态方法
        if (lc.isStarting()) {
			return STARTING;
		}
        if (lc.isStarted()) {
			return STARTED;
		}
        if (lc.isStopping()) {
			return STOPPING;
		}
        if (lc.isStopped()) {
			return STOPPED;
		}
        return FAILED;
    }

	//**********************************************************************
	private void setStarting() {// 在启动函数中调用
		if (LOG.isDebugEnabled()) {
			LOG.debug("starting {}", this);
		}
		_state = __STARTING;
		for (Listener listener : _listeners) {// 调用监听器中的事件处理函数
			listener.lifeCycleStarting(this);
		}
	}
    private void setStarted() {
        _state = __STARTED;
        if (LOG.isDebugEnabled()) {
			LOG.debug(STARTED + " @{}ms {}", Uptime.getUptime(), this);// ?????
		}
        for (Listener listener : _listeners) {
			listener.lifeCycleStarted(this);
		}
    }
    private void setStopping() {
        if (LOG.isDebugEnabled()) {
			LOG.debug("stopping {}", this);
		}
        _state = __STOPPING;
        for (Listener listener : _listeners) {
			listener.lifeCycleStopping(this);
		}
    }
    private void setStopped() {
        _state = __STOPPED;
        if (LOG.isDebugEnabled()) {
			LOG.debug("{} {}", STOPPED, this);
		}
        for (Listener listener : _listeners) {
			listener.lifeCycleStopped(this);
		}
    }
    private void setFailed(Throwable th) {
        _state = __FAILED;
        if (LOG.isDebugEnabled()) {
			LOG.warn(FAILED + " " + this + ": " + th, th);
		}
        for (Listener listener : _listeners) {
			listener.lifeCycleFailure(this, th);
		}
    }
	//**********************************************************************
	private long _stopTimeout = 30000;// 用于设置停止程序的超时时间--表示停止程序的过程不能超过该时间长度(默认)
    public long getStopTimeout() {
        return _stopTimeout;
    }
    public void setStopTimeout(long stopTimeout) {
        this._stopTimeout = stopTimeout;
    }

	// **********************************************************************
	public static abstract class AbstractLifeCycleListener implements LifeCycle.Listener {// 默认空实现
        @Override public void lifeCycleFailure(LifeCycle event, Throwable cause) {}
        @Override public void lifeCycleStarted(LifeCycle event) {}
        @Override public void lifeCycleStarting(LifeCycle event) {}
        @Override public void lifeCycleStopped(LifeCycle event) {}
        @Override public void lifeCycleStopping(LifeCycle event) {}
    }
}
