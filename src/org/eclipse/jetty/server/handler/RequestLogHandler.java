package org.eclipse.jetty.server.handler;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;

public class RequestLogHandler extends HandlerWrapper {

    private RequestLog _requestLog;

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (baseRequest.getDispatcherType() == DispatcherType.REQUEST) {
			baseRequest.getHttpChannel().addRequestLog(_requestLog);
		}
		if (_handler != null) {
			_handler.handle(target, baseRequest, request, response);
		}
    }

	public void setRequestLog(RequestLog requestLog) {
		updateBean(_requestLog, requestLog);
		_requestLog = requestLog;
    }

	public RequestLog getRequestLog() {
        return _requestLog;
    }
}
