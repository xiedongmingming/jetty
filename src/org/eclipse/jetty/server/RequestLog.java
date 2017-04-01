package org.eclipse.jetty.server;

public interface RequestLog {
    public void log(Request request, Response response);
}
