package org.eclipse.jetty.embedded;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;

import extral.org.eclipse.jetty.util.ajax.JSON;

public class ManyHandlers {

	public static class ParamHandler extends AbstractHandler {
		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
            Map<String, String[]> params = request.getParameterMap();
			if (params.size() > 0) {
                response.setContentType("text/plain");
                response.getWriter().println(JSON.toString(params));
                baseRequest.setHandled(true);
            }
        }
    }

	public static class WelcomeWrapHandler extends HandlerWrapper {
        @Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
            request.setAttribute("welcome", "Hello");
            super.handle(target, baseRequest, request, response);
        }
    }

    public static void main( String[] args ) throws Exception {

		Server server = new Server(8080);

        // create the handlers
        Handler param = new ParamHandler();
        HandlerWrapper wrapper = new WelcomeWrapHandler();
        Handler hello = new HelloHandler();
        Handler dft = new DefaultHandler();
        RequestLogHandler requestLog = new RequestLogHandler();

        // configure request logging
        File requestLogFile = File.createTempFile("demo", "log");
		NCSARequestLog ncsaLog = new NCSARequestLog(requestLogFile.getAbsolutePath());
        requestLog.setRequestLog(ncsaLog);

        // create the handler collections
        HandlerCollection handlers = new HandlerCollection();
        HandlerList list = new HandlerList();

        // link them all together
        wrapper.setHandler(hello);
        list.setHandlers(new Handler[] { param, new GzipHandler(), dft });
        handlers.setHandlers(new Handler[] { list, requestLog });

        // Handler tree looks like the following
        // <pre>
        // Server
        // + HandlerCollection
        // . + HandlerList
        // . | + param (ParamHandler)
        // . | + wrapper (WelcomeWrapHandler)
        // . | | \ hello (HelloHandler)
        // . | \ dft (DefaultHandler)
        // . \ requestLog (RequestLogHandler)
        // </pre>
        server.setHandler(handlers);
        server.start();
        server.join();
    }
}
