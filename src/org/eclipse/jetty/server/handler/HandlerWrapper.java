package org.eclipse.jetty.server.handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Request;

public class HandlerWrapper extends AbstractHandlerContainer {// 是实现最彻底的处理器--委托时使用

	// 这个类主要是对抽象处理器容器的包装

	// 生命周期类
    
	protected Handler _handler;

	// *********************************************************************************
	public HandlerWrapper() {// 构造器

    }

	// *********************************************************************************
    public Handler getHandler() {
        return _handler;
    }
	public void setHandler(Handler handler) {// 设置该包装器中包装的处理器(可以是符合类型--主要是处理器集合类)

		if (isStarted()) {// 必须在该容器启动之前
			throw new IllegalStateException(STARTED);
		}

		if (handler == this || (handler instanceof HandlerContainer && Arrays.asList(((HandlerContainer) handler).getChildHandlers()).contains(this))) {
			// 表示待设置的处理器包含了该包装器
			throw new IllegalStateException("sethandler loop");
		}
        
		if (handler != null) {
			handler.setServer(getServer());
        }
        
		Handler old = _handler;
        
		_handler = handler;
		
		updateBean(old, _handler, true);// 作为BEAN处理(本身是个容器)
    }
	public void insertHandler(HandlerWrapper wrapper) {

		if (wrapper == null) {
			throw new IllegalArgumentException();
        }

        HandlerWrapper tail = wrapper;

		while (tail.getHandler() instanceof HandlerWrapper) {// 找到包装器中包装的抽象处理器容器
			tail = (HandlerWrapper) tail.getHandler();
		}

		if (tail.getHandler() != null) {
            throw new IllegalArgumentException("bad tail of inserted wrapper chain");
        }

        Handler next = getHandler();

        setHandler(wrapper);

        tail.setHandler(next);
    }

	// *********************************************************************************
	// 处理器容器接口函数: HandlerContainer
	@Override
	public Handler[] getHandlers() {
		if (_handler == null) {
			return new Handler[0];
		}
		return new Handler[] { _handler };
	}
	// *********************************************************************************
	// HANDLER的接口函数: Handler
    @Override
	public void handle(String target, // 请求的路径(主机名后的路径)
			Request baseRequest, // 具体实现类
			HttpServletRequest request, // 接口类
			HttpServletResponse response) throws IOException, ServletException {

		Handler handler = _handler;// HandlerCollection、ContextHandler

        if (handler != null) {
			handler.handle(target, baseRequest, request, response);// 处理器容器中的处理:ScopedHandler.handle、HandlerCollection.handle
		}
    }
	// *********************************************************************************
	// 父类接口实现: AbstractHandlerContainer
    @Override
    protected void expandChildren(List<Handler> list, Class<?> byClass) {
		expandHandler(_handler, list, byClass);// 调用父类中的方法
    }
	// *********************************************************************************
	// 重写的接口: AbstractHandler
    @Override
	public void destroy() {// 必须停止后销毁

		if (!isStopped()) {// 表示还没有停止
			throw new IllegalStateException("!STOPPED");
		}

		Handler child = getHandler();// 包装的处理器

        if (child != null) {
            setHandler(null);
			child.destroy();// 包装的处理器销毁
        }

		super.destroy();// 调用父类响应函数
    }
	// *********************************************************************************
}
