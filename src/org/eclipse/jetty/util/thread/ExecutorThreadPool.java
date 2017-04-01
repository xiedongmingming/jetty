package org.eclipse.jetty.util.thread;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ExecutorThreadPool extends AbstractLifeCycle implements ThreadPool, LifeCycle {// 功能类似于--QueuedThreadPool

    private static final Logger LOG = Log.getLogger(ExecutorThreadPool.class);

	private final ExecutorService _executor;

	// **************************************************************************************
	public ExecutorThreadPool(ExecutorService executor) {// 最终实现
        _executor = executor;
    }
	public ExecutorThreadPool() {// ThreadPoolExecutor
        this(new ThreadPoolExecutor(256, 256, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()));
    }
	public ExecutorThreadPool(int queueSize) {
		this(queueSize < 0 ? 
			new ThreadPoolExecutor(256, 256, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>())
			: queueSize == 0
			? new ThreadPoolExecutor(32, 256, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>())
			: new ThreadPoolExecutor(32, 256, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize)));
    }
	public ExecutorThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS);
    }
	public ExecutorThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>());
    }
	public ExecutorThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {

		// 共需要5个参数:
		// corePoolSize -- 32/256
		// maximumPoolSize -- 256
		// keepAliveTime -- 60
		// unit -- TimeUnit.SECONDS
		// workQueue -- LinkedBlockingQueue<Runnable>、SynchronousQueue<Runnable>、ArrayBlockingQueue<Runnable>

		this(new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue));
    }
	// ***************************************************************************************************

    @Override
	public void execute(Runnable job) {// 提交任务
        _executor.execute(job);
    }

	// ******************************************************************************
	@Override
	public void join() throws InterruptedException {
		_executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
	}
	@Override
	public int getIdleThreads() {
		if (_executor instanceof ThreadPoolExecutor) {
            final ThreadPoolExecutor tpe = (ThreadPoolExecutor)_executor;
			return tpe.getPoolSize() - tpe.getActiveCount();//
        }
        return -1;
    }
	@Override
	public int getThreads() {
		if (_executor instanceof ThreadPoolExecutor) {
            final ThreadPoolExecutor tpe = (ThreadPoolExecutor)_executor;
            return tpe.getPoolSize();
        }
        return -1;
    }
	@Override
	public boolean isLowOnThreads() {
		if (_executor instanceof ThreadPoolExecutor) {
            final ThreadPoolExecutor tpe = (ThreadPoolExecutor)_executor;
			return tpe.getPoolSize() == tpe.getMaximumPoolSize() && tpe.getQueue().size() >= tpe.getPoolSize() - tpe.getActiveCount();
        }
        return false;
    }

	// ******************************************************************************
    @Override
	protected void doStop() throws Exception {// 只负责关闭
        super.doStop();
        _executor.shutdownNow();
    }

	// ******************************************************************************
	public boolean dispatch(Runnable job) {
		try {
			_executor.execute(job);
			return true;
		} catch (RejectedExecutionException e) {
			LOG.warn(e);
			return false;
		}
	}
}
