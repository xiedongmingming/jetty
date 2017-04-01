package org.eclipse.jetty.util.thread;

import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.component.LifeCycle;

public interface Scheduler extends LifeCycle {
	interface Task {// 调度时的返回值用于取消被调度的任务
        boolean cancel();
    }
	Task schedule(Runnable task, long delay, TimeUnit units);// 表示对任务进行调度--后面两个参数表示延迟多长时间后再调度
}
