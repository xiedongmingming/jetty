package org.eclipse.jetty.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.eclipse.jetty.http.MimeTypes.Type;
import org.eclipse.jetty.util.resource.Resource;

public interface HttpContent {// 共19个接口函数

    HttpField getContentType();
    String getContentTypeValue();
    String getCharacterEncoding();
    Type getMimeType();

    HttpField getContentEncoding();
    String getContentEncodingValue();
    
    HttpField getContentLength();
    long getContentLengthValue();
    
    HttpField getLastModified();
    String getLastModifiedValue();
    
    HttpField getETag();
    String getETagValue();
    
    ByteBuffer getIndirectBuffer();
    ByteBuffer getDirectBuffer();
    Resource getResource();
    InputStream getInputStream() throws IOException;
    ReadableByteChannel getReadableByteChannel() throws IOException;
    void release();

    HttpContent getGzipContent();
    
    
	public interface Factory {
		HttpContent getContent(String path, int maxBuffer) throws IOException;
    }
}
