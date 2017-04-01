package org.eclipse.jetty.server.handler;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;

public abstract class ScopedHandler extends HandlerWrapper {// 拦截器实现(可以嵌套)

	private static final ThreadLocal<ScopedHandler> __outerScope = new ThreadLocal<ScopedHandler>();

	protected ScopedHandler _outerScope;// 外部处理
	protected ScopedHandler _nextScope;// 下一个处理

	// *********************************************************************************
	// 生命周期函数
    @Override
	protected void doStart() throws Exception {// ???

        try {

			_outerScope = __outerScope.get();//

            if (_outerScope == null) {
                __outerScope.set(this);
			}

            super.doStart();

			_nextScope = getChildHandlerByClass(ScopedHandler.class);// 子类

        } finally {
            if (_outerScope == null) {
                __outerScope.set(null);
			}
        }
    }

	// *********************************************************************************
	// 起到拦截器的作用(HANDLER的接口函数:Handler)
    @Override
    public final void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		if (isStarted()) {
			if (_outerScope == null) {// 为空
				doScope(target, baseRequest, request, response);// 底层实现--ContextHandler
            } else {
                doHandle(target, baseRequest, request, response);
			}
        }
    }
	// *********************************************************************************
    public abstract void doScope(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
    public abstract void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
    public final void nextScope(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (_nextScope != null) {
            _nextScope.doScope(target, baseRequest, request, response);
        } else if (_outerScope != null) {
            _outerScope.doHandle(target, baseRequest, request, response);
		} else {
            doHandle(target, baseRequest, request, response);
		}
    }
    public final void nextHandle(String target, final Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		if (_nextScope != null && _nextScope == _handler) {// ???
            _nextScope.doHandle(target, baseRequest, request, response);
        } else if (_handler != null) {
            _handler.handle(target, baseRequest, request, response);
		}
    }
    protected boolean never() {
        return false;
    }
	// *********************************************************************************
}
