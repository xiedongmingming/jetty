package org.eclipse.jetty.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.eclipse.jetty.http.MimeTypes.Type;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.resource.Resource;

public class ResourceHttpContent implements HttpContent {

	final Resource _resource;
    final String _contentType;
    final int _maxBuffer;
    HttpContent _gzip;
    String _etag;

	// **************************************************************************
	public ResourceHttpContent(final Resource resource, final String contentType) {
		this(resource, contentType, -1, null);
    }
	public ResourceHttpContent(final Resource resource, final String contentType, int maxBuffer) {
		this(resource, contentType, maxBuffer, null);
    }
	public ResourceHttpContent(final Resource resource, final String contentType, int maxBuffer, HttpContent gzip) {
		_resource = resource;
		_contentType = contentType;
		_maxBuffer = maxBuffer;
		_gzip = gzip;
    }
	// **************************************************************************

    @Override
	public String getContentTypeValue() {
        return _contentType;
    }
    @Override
	public HttpField getContentType() {
        return _contentType==null?null:new HttpField(HttpHeader.CONTENT_TYPE,_contentType);
    }
    @Override
	public HttpField getContentEncoding() {
        return null;
    }
    @Override
	public String getContentEncodingValue() {
        return null;
    }
    @Override
	public String getCharacterEncoding() {
        return _contentType==null?null:MimeTypes.getCharsetFromContentType(_contentType);
    }
    @Override
	public Type getMimeType() {
        return _contentType==null?null:MimeTypes.CACHE.get(MimeTypes.getContentTypeWithoutCharset(_contentType));
    }
    @Override
	public HttpField getLastModified() {
        long lm = _resource.lastModified();
        return lm>=0?new HttpField(HttpHeader.LAST_MODIFIED,DateGenerator.formatDate(lm)):null;
    }
    @Override
	public String getLastModifiedValue() {
        long lm = _resource.lastModified();
        return lm>=0?DateGenerator.formatDate(lm):null;
    }
    @Override
	public ByteBuffer getDirectBuffer() {
		if (_resource.length() <= 0 || _maxBuffer > 0 && _maxBuffer < _resource.length()) {
			return null;
		}
		try {
            return BufferUtil.toBuffer(_resource,true);
		} catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	@Override
	public HttpField getETag() {
        return new HttpField(HttpHeader.ETAG,getETagValue());
    }
    @Override
	public String getETagValue() {
        return _resource.getWeakETag();
    }
    @Override
	public ByteBuffer getIndirectBuffer() {
		if (_resource.length() <= 0 || _maxBuffer > 0 && _maxBuffer < _resource.length()) {
			return null;
		}
		try {
            return BufferUtil.toBuffer(_resource,false);
		} catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
	public HttpField getContentLength() {
        long l=_resource.length();
        return l==-1?null:new HttpField.LongValueHttpField(HttpHeader.CONTENT_LENGTH,_resource.length());
    }
    @Override
	public long getContentLengthValue() {
        return _resource.length();
    }
    @Override
	public InputStream getInputStream() throws IOException {
        return _resource.getInputStream();
    }

	@Override
	public ReadableByteChannel getReadableByteChannel() throws IOException {
        return _resource.getReadableByteChannel();
    }
    @Override
	public Resource getResource() {
        return _resource;
    }
    @Override
	public void release() {
        _resource.close();
    }
    @Override
	public String toString() {
        return String.format("%s@%x{r=%s,gz=%b}",this.getClass().getSimpleName(),hashCode(),_resource,_gzip!=null);
    }
    @Override
	public HttpContent getGzipContent() {
		return _gzip == null ? null : new GzipHttpContent(this, _gzip);
    }
}