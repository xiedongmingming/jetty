package org.eclipse.jetty.util.resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

class BadResource extends URLResource {// 无效资源

	private String _message = null;// 无效的原因

	// ******************************************************************************************************************************
	BadResource(URL url, String message) {
		super(url, null);
		_message = message;
    }

	// ******************************************************************************************************************************
    @Override
	public boolean exists() {
        return false;
    }
    @Override
	public long lastModified() {
        return -1;
    }
    @Override
	public boolean isDirectory() {
        return false;
    }
    @Override
	public long length() {
        return -1;
    }
    @Override
	public File getFile() {
        return null;
    }
    @Override
	public InputStream getInputStream() throws IOException {
        throw new FileNotFoundException(_message);
    }
    @Override
	public boolean delete() throws SecurityException {
        throw new SecurityException(_message);
    }
    @Override
	public boolean renameTo(Resource dest) throws SecurityException {
        throw new SecurityException(_message);
    }
    @Override
	public String[] list() {
        return null;
    }
    @Override
	public void copyTo(File destination) throws IOException {
        throw new SecurityException(_message);
    }
    @Override
	public String toString() {
		return super.toString() + "; BadResource=" + _message;
    }
}
