package org.eclipse.jetty.server.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.DateCache;
import org.eclipse.jetty.util.RolloverFileOutputStream;

@Deprecated
public class DebugHandler extends HandlerWrapper implements Connection.Listener {

	private DateCache _date = new DateCache("HH:mm:ss", Locale.US);
    private OutputStream _out;
    private PrintStream _print;

    @Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		final Response base_response = baseRequest.getResponse();
        final Thread thread=Thread.currentThread();
        final String old_name=thread.getName();

        boolean suspend=false;
        boolean retry=false;
        String name=(String)request.getAttribute("org.eclipse.jetty.thread.name");
		if (name == null) {
			name = old_name + ":" + baseRequest.getHttpURI();
		} else {
			retry = true;
		}

        String ex=null;
		try {
			if (retry) {
				print(name, "RESUME");
			} else {
				print(name, "REQUEST " + baseRequest.getRemoteAddr() + " " + request.getMethod() + " " + baseRequest.getHeader("Cookie") + "; " + baseRequest.getHeader("User-Agent"));
			}
            thread.setName(name);

            getHandler().handle(target,baseRequest,request,response);
		} catch (IOException ioe) {
			ex = ioe.toString();
            throw ioe;
		} catch (ServletException se) {
			ex = se.toString() + ":" + se.getCause();
            throw se;
		} catch (RuntimeException rte) {
			ex = rte.toString();
            throw rte;
		} catch (Error e) {
			ex = e.toString();
            throw e;
		} finally {
            thread.setName(old_name);
			suspend = baseRequest.getHttpChannelState().isSuspended();
			if (suspend) {
                request.setAttribute("org.eclipse.jetty.thread.name",name);
                print(name,"SUSPEND");
			} else {
				print(name, "RESPONSE " + base_response.getStatus() + (ex == null ? "" : ("/" + ex)) + " "
						+ base_response.getContentType());
            }
        }
    }
	private void print(String name, String message) {

		long now = System.currentTimeMillis();

        final String d=_date.formatNow(now);

		final int ms = (int) (now % 1000);

		_print.println(d + (ms > 99 ? "." : (ms > 9 ? ".0" : ".00")) + ms + ":" + name + " " + message);
    }
    @Override
	protected void doStart() throws Exception {
		if (_out == null) {
			_out = new RolloverFileOutputStream("./logs/yyyy_mm_dd.debug.log", true);
		}
        _print=new PrintStream(_out);
		for (Connector connector : getServer().getConnectors()) {
			if (connector instanceof AbstractConnector) {
				((AbstractConnector) connector).addBean(this, false);
			}
		}
        super.doStart();
    }
    @Override
	protected void doStop() throws Exception {
        super.doStop();
        _print.close();
		for (Connector connector : getServer().getConnectors()) {
			if (connector instanceof AbstractConnector) {
				((AbstractConnector) connector).removeBean(this);
			}
		}
    }

	public OutputStream getOutputStream() {
        return _out;
    }
	public void setOutputStream(OutputStream out) {
        _out = out;
    }
    
    @Override
	public void onOpened(Connection connection) {
        print(Thread.currentThread().getName(),"OPENED "+connection.toString());
    }
    @Override
	public void onClosed(Connection connection) {
        print(Thread.currentThread().getName(),"CLOSED "+connection.toString());
    }
}
