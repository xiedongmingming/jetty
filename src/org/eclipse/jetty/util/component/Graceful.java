package org.eclipse.jetty.util.component;

import java.util.concurrent.Future;

public interface Graceful {// a lifecycle that can be gracefully shutdown.
	public Future<Void> shutdown();// ���ص�ֵ�������ڿ���ִ�й���(��Ҫ����ȡ������)
}
