package org.eclipse.jetty.server.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Server;

public abstract class AbstractHandlerContainer extends AbstractHandler implements HandlerContainer {

	// 这个类主要是处理容器中处理器相关的事情--本身也是一个处理器

	// *********************************************************************************
	public AbstractHandlerContainer() {

    }

	// *********************************************************************************
	// 处理器容器接口函数(少一个): HandlerContainer
    @Override
    public Handler[] getChildHandlers() {
        List<Handler> list = new ArrayList<>();
        expandChildren(list, null);
        return list.toArray(new Handler[list.size()]);
    }
    @Override
	public Handler[] getChildHandlersByClass(Class<?> byclass) {// 表示获取服务器中指定类下的HANDLER
        List<Handler> list = new ArrayList<>();
        expandChildren(list, byclass);
        return list.toArray(new Handler[list.size()]);
    }
	@SuppressWarnings("unchecked")
	@Override
    public <T extends Handler> T getChildHandlerByClass(Class<T> byclass) {
        List<Handler> list = new ArrayList<>();
        expandChildren(list, byclass);
        if (list.isEmpty()) {
            return null;
		}
		return (T) list.get(0);// 只获取一个
    }
	//*********************************************************************************
	protected void expandChildren(List<Handler> list, Class<?> byClass) {
		// 由子类实现--将容器中的所有处理器存放到第一个参数中(第二个参数表示根据指定类来查找)
	}
	protected void expandHandler(Handler handler, List<Handler> list, Class<?> byClass) {// 供子类调用

		// 第一个参数表示子类的处理器
		// 第二个参数表示扩展找到的处理器列表
		// 第三个参数表示指定类

		if (handler == null) {
            return;
		}

		if (byClass == null || byClass.isAssignableFrom(handler.getClass())) {// 用于测试是否是指定类的父类或者父接口
			// 当指定类为空或者是子类处理器的父类时
			list.add(handler);// 直接包含
		}

        if (handler instanceof AbstractHandlerContainer) {
			((AbstractHandlerContainer) handler).expandChildren(list, byClass);
        } else if (handler instanceof HandlerContainer) {
			HandlerContainer container = (HandlerContainer) handler;
            Handler[] handlers = byClass == null ? container.getChildHandlers() : container.getChildHandlersByClass(byClass);
            list.addAll(Arrays.asList(handlers));
        }
    }
    public static <T extends HandlerContainer> T findContainerOf(HandlerContainer root, Class<T>type, Handler handler) {
        if (root == null || handler == null) {
            return null;
		}
        Handler[] branches = root.getChildHandlersByClass(type);
        if (branches != null) {
            for (Handler h : branches) {
                @SuppressWarnings("unchecked")
                T container = (T)h;
                Handler[] candidates = container.getChildHandlersByClass(handler.getClass());
                if (candidates != null) {
                    for (Handler c : candidates) {
                        if (c == handler) {
                            return container;
						}
					}
                }
            }
        }
        return null;
    }

	// *********************************************************************************
    @Override
	public void setServer(Server server) {// 对自身及其中包含的处理器设置服务
        if (server == getServer()) {
            return;
		}
        if (isStarted()) {
            throw new IllegalStateException(STARTED);
		}
        super.setServer(server);
        Handler[] handlers = getHandlers();
        if (handlers != null) {
            for (Handler h : handlers) {
                h.setServer(server);
			}
		}
    }
	// *********************************************************************************
}
