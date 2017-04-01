package org.eclipse.jetty.util.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.ReadableByteChannel;
import java.security.Permission;

import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class URLResource extends Resource {//

    private static final Logger LOG = Log.getLogger(URLResource.class);

	protected final URL _url;
	protected final String _urlString;
	protected URLConnection _connection;
	transient boolean _useCaches = Resource.__defaultUseCaches;
	protected InputStream _in = null;


	// ******************************************************************************
	protected URLResource(URL url, URLConnection connection) {
        _url = url;
		_urlString = _url.toExternalForm();
		_connection = connection;
    }
	protected URLResource(URL url, URLConnection connection, boolean useCaches) {
		this(url, connection);
        _useCaches = useCaches;
    }
	// ******************************************************************************

	protected synchronized boolean checkConnection() {
		if (_connection == null) {
			try {
				_connection = _url.openConnection();
                _connection.setUseCaches(_useCaches);
			} catch (IOException e) {
                LOG.ignore(e);
            }
        }
		return _connection != null;
    }
    @Override
	public synchronized void close() {
		if (_in != null) {
			try {
				_in.close();
			} catch (IOException e) {
				LOG.ignore(e);
			}
            _in=null;
        }
		if (_connection != null) {
			_connection = null;
		}
    }
    @Override
	public boolean exists() {
		try {
			synchronized (this) {
				if (checkConnection() && _in == null) {
					_in = _connection.getInputStream();
				}
            }
		} catch (IOException e) {
            LOG.ignore(e);
        }
        return _in!=null;
    }
    @Override
	public boolean isDirectory() {
        return exists() && _urlString.endsWith("/");
    }
    @Override
	public long lastModified() {
		if (checkConnection()) {
			return _connection.getLastModified();
		}
        return -1;
    }
    @Override
	public long length() {
		if (checkConnection()) {
			return _connection.getContentLength();
		}
        return -1;
    }
    @Override
	public URL getURL() {
        return _url;
    }
    @Override
	public File getFile() throws IOException {
		if (checkConnection()) {
            Permission perm = _connection.getPermission();
			if (perm instanceof java.io.FilePermission) {
				return new File(perm.getName());
			}
        }
		try {
			return new File(_url.getFile());
		} catch (Exception e) {
			LOG.ignore(e);
		}
        return null;    
    }
    @Override
	public String getName() {
        return _url.toExternalForm();
    }
    @Override
	public synchronized InputStream getInputStream() throws java.io.IOException {
		return getInputStream(true);
    }
	protected synchronized InputStream getInputStream(boolean resetConnection) throws IOException {
		if (!checkConnection()) {
            throw new IOException( "Invalid resource");
		}
		try {
			if (_in != null) {
                InputStream in = _in;
                _in=null;
                return in;
            }
            return _connection.getInputStream();
		} finally {
			if (resetConnection) {
                _connection=null;
				if (LOG.isDebugEnabled()) {
					LOG.debug("Connection nulled");
				}
            }
        }
    }
    @Override
	public ReadableByteChannel getReadableByteChannel() throws IOException {
        return null;
    }
    @Override
	public boolean delete() throws SecurityException {
        throw new SecurityException( "Delete not supported");
    }
    @Override
	public boolean renameTo(Resource dest) throws SecurityException {
        throw new SecurityException( "RenameTo not supported");
    }
    @Override
	public String[] list() {
        return null;
    }
    @Override
	public Resource addPath(String path) throws IOException, MalformedURLException {
		if (path == null) {
            return null;
		}
        path = URIUtil.canonicalPath(path);
        return newResource(URIUtil.addPaths(_url.toExternalForm(),URIUtil.encodePath(path)), _useCaches);
    }
    @Override
	public String toString() {
        return _urlString;
    }
    @Override
	public int hashCode() {
        return _urlString.hashCode();
    }
    @Override
	public boolean equals(Object o) {
        return o instanceof URLResource && _urlString.equals(((URLResource)o)._urlString);
    }

	public boolean getUseCaches() {
        return _useCaches;
    }
    @Override
	public boolean isContainedIn(Resource containingResource) throws MalformedURLException {
        return false;
    }
}
