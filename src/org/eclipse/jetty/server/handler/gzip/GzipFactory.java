package org.eclipse.jetty.server.handler.gzip;

import java.util.zip.Deflater;

import org.eclipse.jetty.server.Request;

public interface GzipFactory {

	Deflater getDeflater(Request request, long content_length);

    boolean isMimeTypeGzipable(String mimetype);

    void recycle(Deflater deflater);
}
