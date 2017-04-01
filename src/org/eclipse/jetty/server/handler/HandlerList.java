package org.eclipse.jetty.server.handler;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;

public class HandlerList extends HandlerCollection {

    @Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
        Handler[] handlers = getHandlers();
        if (handlers != null && isStarted()) {
            for (int i = 0; i < handlers.length; i++) {
                handlers[i].handle(target, baseRequest, request, response);
                if (baseRequest.isHandled()) return;
            }
        }
    }
}
