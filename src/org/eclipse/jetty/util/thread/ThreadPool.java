package org.eclipse.jetty.util.thread;

import java.util.concurrent.Executor;

public interface ThreadPool extends Executor {// 继承的接口只包含一个函数: execute

    public void join() throws InterruptedException;
    public int getThreads();
    public int getIdleThreads();
    public boolean isLowOnThreads();

	public interface SizedThreadPool extends ThreadPool {// 表示限定最大值和最小值的线程池
        public int getMinThreads();
        public int getMaxThreads();
        public void setMinThreads(int threads);
        public void setMaxThreads(int threads);
    }
}
