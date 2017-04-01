package org.eclipse.jetty.util.component;

import java.util.concurrent.Future;

public interface Graceful {// a lifecycle that can be gracefully shutdown.
	public Future<Void> shutdown();// 返回的值可以用于控制执行过程(主要用于取消过程)
}
