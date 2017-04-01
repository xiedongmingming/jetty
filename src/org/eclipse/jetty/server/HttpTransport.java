package org.eclipse.jetty.server;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;

public interface HttpTransport {// ?????
    void send(MetaData.Response info, boolean head, ByteBuffer content, boolean lastContent, Callback callback);
    boolean isPushSupported();//?????
    void push(MetaData.Request request);
    void onCompleted();
    void abort(Throwable failure);
    boolean isOptimizedForDirectBuffers();
}
