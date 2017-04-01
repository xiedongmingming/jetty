package org.eclipse.jetty.util.thread;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.util.component.Destroyable;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ShutdownThread extends Thread {//管理所有具有生命周期的对象的关闭操作 -- 执行停止函数和销毁函数

    private static final Logger LOG = Log.getLogger(ShutdownThread.class);
	
    private static final ShutdownThread _thread = new ShutdownThread();//单例

    private boolean _hooked;//表示是否挂上钩子了
	
    private final List<LifeCycle> _lifeCycles = new CopyOnWriteArrayList<LifeCycle>();//管理的所有生命周期对象

	//***************************************************************************
    private ShutdownThread() {
    }
	//***************************************************************************
    private synchronized void hook() {//执行挂载操作
        try {
            if (!_hooked) {
				Runtime.getRuntime().addShutdownHook(this);
			}
            _hooked = true;
        } catch(Exception e) {
            LOG.ignore(e);
            LOG.info("shutdown already commenced");
        }
    }
    private synchronized void unhook() {//执行卸载操作
        try {
            _hooked = false;
            Runtime.getRuntime().removeShutdownHook(this);
        } catch(Exception e) {
            LOG.ignore(e);
            LOG.debug("shutdown already commenced");
        }
    }
	//***************************************************************************
    public static ShutdownThread getInstance() {
        return _thread;
    }
	//***************************************************************************
    public static synchronized void register(LifeCycle... lifeCycles) {//注册
        _thread._lifeCycles.addAll(Arrays.asList(lifeCycles));
        if (_thread._lifeCycles.size() > 0) {
			_thread.hook();
		}
    }
    public static synchronized void register(int index, LifeCycle... lifeCycles) {//在指定位置插入参数中的生命周期对象
        _thread._lifeCycles.addAll(index, Arrays.asList(lifeCycles));
        if (_thread._lifeCycles.size() > 0) {
            _thread.hook();
		}
    }
    public static synchronized void deregister(LifeCycle lifeCycle) {
        _thread._lifeCycles.remove(lifeCycle);
        if (_thread._lifeCycles.size() == 0) {
            _thread.unhook();
		}
    }
    public static synchronized boolean isRegistered(LifeCycle lifeCycle) {
        return _thread._lifeCycles.contains(lifeCycle);
    }
	//***************************************************************************
    @Override
    public void run() {//执行钩子时调用的代码块
        for (LifeCycle lifeCycle : _thread._lifeCycles) {
            try {
                if (lifeCycle.isStarted()) {
                    lifeCycle.stop();
                    LOG.debug("Stopped {}", lifeCycle);
                }
                if (lifeCycle instanceof Destroyable) {//停止之后就可以销毁了
                    ((Destroyable)lifeCycle).destroy();
                    LOG.debug("Destroyed {}", lifeCycle);
                }
            } catch (Exception ex) {
                LOG.debug(ex);
            }
        }
    }
}
