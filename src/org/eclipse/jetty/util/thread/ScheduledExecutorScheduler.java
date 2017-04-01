package org.eclipse.jetty.util.thread;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;

public class ScheduledExecutorScheduler extends AbstractLifeCycle implements Scheduler, Dumpable {

	// 用于包装调度器

	private final String name;
	private final boolean daemon;
	private final ClassLoader classloader;
	private final ThreadGroup threadGroup;
	private volatile ScheduledThreadPoolExecutor scheduler;// 库类类
	private volatile Thread thread;// 调度器产生的唯一线程

	// ********************************************************************************
	public ScheduledExecutorScheduler() {// 连接器的默认调度器
        this(null, false);
    }  
	public ScheduledExecutorScheduler(String name, boolean daemon) {
		this(name, daemon, Thread.currentThread().getContextClassLoader());//
    }
	public ScheduledExecutorScheduler(String name, boolean daemon, ClassLoader threadFactoryClassLoader) {
        this(name, daemon, threadFactoryClassLoader, null);
    }
	public ScheduledExecutorScheduler(String name, //默认值为空
			boolean daemon, // 默认值为空
			ClassLoader threadFactoryClassLoader, // 默认值为空
			ThreadGroup threadGroup) {// 默认值为空
        this.name = name == null ? "Scheduler-" + hashCode() : name;
        this.daemon = daemon;
        this.classloader = threadFactoryClassLoader == null ? Thread.currentThread().getContextClassLoader() : threadFactoryClassLoader;
        this.threadGroup = threadGroup;
    }
	// ********************************************************************************

    @Override
	protected void doStart() throws Exception {// 启动连接器的调度器--作为连接器的BEAN启动的
		scheduler = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {// 第一个参数表示线程池中的线程维持数量,第二参数表示线程工厂
            @Override
			public Thread newThread(Runnable r) {
				System.out.println("调度器产生过多少个线程统计!!!!!!!!!!!!!!!!!!!!!!!!!");
				Thread thread = ScheduledExecutorScheduler.this.thread = new Thread(threadGroup, r, name);// 生成线程
                thread.setDaemon(daemon);
                thread.setContextClassLoader(classloader);
                return thread;
            }
        });
		scheduler.setRemoveOnCancelPolicy(true);// 表示立即移除
        super.doStart();
    }
    @Override
	protected void doStop() throws Exception {
        scheduler.shutdownNow();
        super.doStop();
        scheduler = null;
    }
	// ********************************************************************************

    @Override
	public Task schedule(Runnable task, long delay, TimeUnit unit) {
        ScheduledThreadPoolExecutor s = scheduler;
		if (s == null) {
			return new Task() {
                @Override
				public boolean cancel() {//
                    return false;
                }};
		}
        ScheduledFuture<?> result = s.schedule(task, delay, unit);
        return new ScheduledFutureTask(result);
    }
	// ********************************************************************************

    @Override
	public String dump() {
        return ContainerLifeCycle.dump(this);
    }
    @Override
	public void dump(Appendable out, String indent) throws IOException {
        ContainerLifeCycle.dumpObject(out, this);
        Thread thread = this.thread;
		if (thread != null) {
            List<StackTraceElement> frames = Arrays.asList(thread.getStackTrace());
            ContainerLifeCycle.dump(out, indent, frames);
        }
    }
	// ********************************************************************************

	private static class ScheduledFutureTask implements Task {
		private final ScheduledFuture<?> scheduledFuture;//
		ScheduledFutureTask(ScheduledFuture<?> scheduledFuture) {
            this.scheduledFuture = scheduledFuture;
        }
        @Override
		public boolean cancel() {
            return scheduledFuture.cancel(false);
        }
    }
}
