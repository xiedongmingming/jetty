package org.eclipse.jetty.util.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardOpenOption;
import java.security.Permission;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

@Deprecated
public class FileResource extends Resource {

    private static final Logger LOG = Log.getLogger(FileResource.class);

    private final File _file;
    private final URI _uri;
    private final URI _alias;

	public FileResource(URL url) throws IOException, URISyntaxException {
        File file;
		try {
            file =new File(url.toURI());
            assertValidPath(file.toString());
		} catch (URISyntaxException e) {
            throw e;
		} catch (Exception e) {
			if (!url.toString().startsWith("file:")) {
				throw new IllegalArgumentException("!file:");
			}
            
            LOG.ignore(e);
			try {
                String file_url="file:"+URIUtil.encodePath(url.toString().substring(5));
                URI uri = new URI(file_url);
				if (uri.getAuthority() == null) {
					file = new File(uri);
				} else {
					file = new File("//" + uri.getAuthority() + URIUtil.decodePath(url.getFile()));
				}
			} catch (Exception e2) {
                LOG.ignore(e2);
                URLConnection connection=url.openConnection();
                Permission perm = connection.getPermission();
				file = new File(perm == null ? url.getFile() : perm.getName());
            }
        }
        
        _file=file;
        _uri=normalizeURI(_file,url.toURI());
        _alias=checkFileAlias(_uri,_file);
    }
	@SuppressWarnings("serial")
	public FileResource(URI uri) {
        File file=new File(uri);
        _file=file;
		try {
            URI file_uri = _file.toURI();
            _uri = normalizeURI(_file, uri);
            assertValidPath(file.toString());
			if (!URIUtil.equalsIgnoreEncodings(_uri.toASCIIString(), file_uri.toString())) {
				_alias = _file.toURI();
			} else {
				_alias = checkFileAlias(_uri, _file);
			}
		} catch (URISyntaxException e) {
			throw new InvalidPathException(_file.toString(), e.getMessage()) {
                {
                    initCause(e);
                }
            };
        }
    }
	@SuppressWarnings("serial")
	public FileResource(File file) {
        assertValidPath(file.toString());
        _file=file;
		try {
            _uri = normalizeURI(_file, _file.toURI());
		} catch (URISyntaxException e) {
			throw new InvalidPathException(_file.toString(), e.getMessage()) {
                {
                    initCause(e);
                }
            };
        }
        _alias=checkFileAlias(_uri,_file);
    }
	@SuppressWarnings("serial")
	public FileResource(File base, String childPath) {
        String encoded = URIUtil.encodePath(childPath);

        _file = new File(base, childPath);

        URI uri;
		try {
			if (base.isDirectory()) {
                uri=new URI(URIUtil.addPaths(base.toURI().toASCIIString(),encoded));
			} else {
                uri=new URI(base.toURI().toASCIIString()+encoded);
            }
		} catch (final URISyntaxException e) {
			throw new InvalidPathException(base.toString() + childPath, e.getMessage()) {
                {
                    initCause(e);
                }
            };
        }

        _uri=uri;
        _alias=checkFileAlias(_uri,_file);
    }

    /* -------------------------------------------------------- */
    private static URI normalizeURI(File file, URI uri) throws URISyntaxException {
        String u =uri.toASCIIString();
        if (file.isDirectory())
        {
            if(!u.endsWith("/"))
                u+="/";
        }
        else if (file.exists() && u.endsWith("/"))
            u=u.substring(0,u.length()-1);
        return new URI(u);
    }

    /* -------------------------------------------------------- */
    private static URI checkFileAlias(final URI uri, final File file)
    {
        try
        {
            if (!URIUtil.equalsIgnoreEncodings(uri,file.toURI()))
            {
                // Return alias pointing to Java File normalized URI
                return new File(uri).getAbsoluteFile().toURI();
            }

            String abs=file.getAbsolutePath();
            String can=file.getCanonicalPath();

            if (!abs.equals(can))
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("ALIAS abs={} can={}",abs,can);

                URI alias=new File(can).toURI();
                // Have to encode the path as File.toURI does not!
                return new URI("file://"+URIUtil.encodePath(alias.getPath()));
            }
        }
        catch(Exception e)
        {
            LOG.warn("bad alias for {}: {}",file,e.toString());
            LOG.debug(e);
            try
            {
                return new URI("http://eclipse.org/bad/canonical/alias");
            }
            catch(Exception e2)
            {
                LOG.ignore(e2);
                throw new RuntimeException(e);
            }
        }

        return null;
    }
    
    /* -------------------------------------------------------- */
    @Override
    public Resource addPath(String path)
            throws IOException, MalformedURLException
    {
        assertValidPath(path);
        path = org.eclipse.jetty.util.URIUtil.canonicalPath(path);

        if (path==null)
            throw new MalformedURLException();
        
        if ("/".equals(path))
            return this;

        return new FileResource(_file, path);
    }

    private void assertValidPath(String path)
    {
        int idx = StringUtil.indexOfControlChars(path);
        if (idx >= 0)
        {
            throw new InvalidPathException(path, "Invalid Character at index " + idx);
        }
    }

    /* ------------------------------------------------------------ */
    @Override
    public URI getAlias()
    {
        return _alias;
    }
    
    /* -------------------------------------------------------- */
    /**
     * Returns true if the resource exists.
     */
    @Override
    public boolean exists()
    {
        return _file.exists();
    }
        
    /* -------------------------------------------------------- */
    /**
     * Returns the last modified time
     */
    @Override
    public long lastModified()
    {
        return _file.lastModified();
    }

    /* -------------------------------------------------------- */
    /**
     * Returns true if the resource is a container/directory.
     */
    @Override
    public boolean isDirectory()
    {
        return _file.exists() && _file.isDirectory() || _uri.toASCIIString().endsWith("/");
    }

    /* --------------------------------------------------------- */
    /**
     * Return the length of the resource
     */
    @Override
    public long length()
    {
        return _file.length();
    }
        

    /* --------------------------------------------------------- */
    /**
     * Returns the name of the resource
     */
    @Override
    public String getName()
    {
        return _file.getAbsolutePath();
    }
        
    /* ------------------------------------------------------------ */
    /**
     * Returns an File representing the given resource or NULL if this
     * is not possible.
     */
    @Override
    public File getFile()
    {
        return _file;
    }
        
    /* --------------------------------------------------------- */
    /**
     * Returns an input stream to the resource
     */
    @Override
    public InputStream getInputStream() throws IOException
    {
        return new FileInputStream(_file);
    }

    /* ------------------------------------------------------------ */
    @Override
    public ReadableByteChannel getReadableByteChannel() throws IOException
    {
        return FileChannel.open(_file.toPath(),StandardOpenOption.READ);
    }
        
    /* --------------------------------------------------------- */
    /**
     * Deletes the given resource
     */
    @Override
    public boolean delete()
            throws SecurityException
    {
        return _file.delete();
    }

    /* --------------------------------------------------------- */
    /**
     * Rename the given resource
     */
    @Override
	public boolean renameTo(Resource dest) throws SecurityException
    {
        if( dest instanceof FileResource)
            return _file.renameTo( ((FileResource)dest)._file);
        else
            return false;
    }
    @Override
	public String[] list() {
        String[] list =_file.list();
		if (list == null) {
			return null;
		}
		for (int i = list.length; i-- > 0;) {
			if (new File(_file, list[i]).isDirectory() && !list[i].endsWith("/")) {
				list[i] += "/";
			}
        }
        return list;
    }
    @Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (null == o || !(o instanceof FileResource)) {
			return false;
		}

        FileResource f=(FileResource)o;
        return f._file == _file || (null != _file && _file.equals(f._file));
    }
    @Override
	public int hashCode() {
        return null == _file ? super.hashCode() : _file.hashCode();
    }
    @Override
	public void copyTo(File destination) throws IOException {
		if (isDirectory()) {
            IO.copyDir(getFile(),destination);
		} else {
			if (destination.exists()) {
				throw new IllegalArgumentException(destination + " exists");
			}
            IO.copy(getFile(),destination);
        }
    }
    @Override
	public boolean isContainedIn(Resource r) throws MalformedURLException {
        return false;
    }
    @Override
	public void close() {
    }
    @Override
	public URL getURL() {
		try {
            return _uri.toURL();
		} catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }
    @Override
	public URI getURI() {
        return _uri;
    }
    @Override
	public String toString() {
        return _uri.toString();
    }
}