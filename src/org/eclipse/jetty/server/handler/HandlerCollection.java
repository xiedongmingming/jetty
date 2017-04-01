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
import org.eclipse.jetty.util.ArrayUtil;
import org.eclipse.jetty.util.MultiException;

public class HandlerCollection extends AbstractHandlerContainer {// 也是一种HANDLER--作为BEAN初始状态为MANAGED--是一个生命周期容器
	
	// 在服务SERVER中是一个BEAN(初始状态为MANAGED)
	//

	private final boolean _mutableWhenRunning;// 是否禁止运行期间添加处理器--默认为假(表示禁止)
	
	private volatile Handler[] _handlers;// 注册的所有处理器--也可能是处理器容器

	//***************************************************************************
	// 构造函数
    public HandlerCollection() {
        _mutableWhenRunning = false;
    }
    public HandlerCollection(boolean mutableWhenRunning) {
        _mutableWhenRunning = mutableWhenRunning;
    }
	//***************************************************************************
    @Override
    public Handler[] getHandlers() {
        return _handlers;
    }

	public void setHandlers(Handler[] handlers) {// 为该处理器集合设置处理器
		if (!_mutableWhenRunning && isStarted()) {// 表示运行期间不能变
			throw new IllegalStateException(STARTED);
		}
        if (handlers != null) {
            for (Handler handler : handlers) {
				if (handler == this || (handler instanceof HandlerContainer
						&& Arrays.asList(((HandlerContainer) handler).getChildHandlers()).contains(this))) {// 最后面的表示子处理器中包含该处理器集合
                    throw new IllegalStateException("sethandler loop");
				}
			}
			for (Handler handler : handlers) {// 关联当前的服务
                if (handler.getServer() != getServer()) {
                    handler.setServer(getServer());
				}
			}
        }
        Handler[] old = _handlers;;
        _handlers = handlers;
        updateBeans(old, handlers);
    }

    @Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {// 请求处理总控制器 -- 分发处理

		System.out.println("请求处理过程: 3. HandlerCollection");

		if (_handlers != null && isStarted()) {

			MultiException mex = null;

			// 各个处理器会依次进行处理(如果已经被标志为处理过则不进行实际的处理)
			for (int i = 0; i < _handlers.length; i++) {// ServletContextHandler、DefaultHandler
                try {
					_handlers[i].handle(target, baseRequest, request, response);//
                } catch(IOException e) {
                    throw e;
                } catch(RuntimeException e) {
                    throw e;
                } catch(Exception e) {
                    if (mex == null) {
                        mex = new MultiException();
					}
                    mex.add(e);
                }
            }

			if (mex != null) {// 表示处理失败
                if (mex.size() == 1) {
                    throw new ServletException(mex.getThrowable(0));
                } else {
                    throw new ServletException(mex);
				}
            }
        }
    }
    public void addHandler(Handler handler) {
        setHandlers(ArrayUtil.addToArray(getHandlers(), handler, Handler.class));
    }
    public void removeHandler(Handler handler) {
        Handler[] handlers = getHandlers();
        if (handlers != null && handlers.length > 0) {
            setHandlers(ArrayUtil.removeFromArray(handlers, handler));
		}
    }
    @Override
    protected void expandChildren(List<Handler> list, Class<?> byClass) {
        if (getHandlers() != null) {
            for (Handler h : getHandlers()) {
                expandHandler(h, list, byClass);
			}
		}
    }
    @Override
    public void destroy() {
		if (!isStopped()) {// 停止之后才能销毁
            throw new IllegalStateException("!STOPPED");
		}
		Handler[] children = getChildHandlers();// 该处理器集合的所有子处理器--父类
        setHandlers(null);
        for (Handler child: children) {
            child.destroy();
		}
        super.destroy();
    }
    @Override
    public String toString() {
		Handler[] handlers = getHandlers();// 当前处理器集合的所有处理器
        return super.toString() + (handlers == null ? "[]" : Arrays.asList(getHandlers()).toString());
    }
}
