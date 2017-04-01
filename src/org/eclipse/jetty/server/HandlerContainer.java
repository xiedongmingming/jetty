package org.eclipse.jetty.server;

import org.eclipse.jetty.util.component.LifeCycle;

public interface HandlerContainer extends LifeCycle {
	public Handler[] getHandlers();// 表示获取容器中的所有处理器
    public Handler[] getChildHandlers();//
    public Handler[] getChildHandlersByClass(Class<?> byclass);
	public <T extends Handler> T getChildHandlerByClass(Class<T> byclass);// 只获取一个
}
