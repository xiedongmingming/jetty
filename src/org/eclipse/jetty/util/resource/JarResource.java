package org.eclipse.jetty.util.resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;


public class JarResource extends URLResource {

    private static final Logger LOG = Log.getLogger(JarResource.class);

    protected JarURLConnection _jarConnection;
    
	// ******************************************************************************
	protected JarResource(URL url) {
        super(url,null);
    }
	protected JarResource(URL url, boolean useCaches) {// jar:
        super(url, null, useCaches);
    }
    
	// ******************************************************************************
    @Override
	public synchronized void close() {
        _jarConnection=null;
        super.close();
    }
    @Override
	protected synchronized boolean checkConnection() {
        super.checkConnection();
		try {
			if (_jarConnection != _connection) {
				newConnection();
			}
		} catch (IOException e) {
            LOG.ignore(e);
            _jarConnection=null;
        }
        
        return _jarConnection!=null;
    }
	protected void newConnection() throws IOException {
        _jarConnection=(JarURLConnection)_connection;
    }
    @Override
	public boolean exists() {
		if (_urlString.endsWith("!/")) {
			return checkConnection();
		} else {
			return super.exists();
		}
    }    
    @Override
	public File getFile() throws IOException {
        return null;
    }
    @Override
	public InputStream getInputStream() throws java.io.IOException {
        checkConnection();
        if (!_urlString.endsWith("!/"))
			return new FilterInputStream(getInputStream(false)) {
                @Override
                public void close() throws IOException {this.in=IO.getClosedStream();}
            };

        URL url = new URL(_urlString.substring(4,_urlString.length()-2));      
        InputStream is = url.openStream();
        return is;
    }
 
    @Override
	public void copyTo(File directory) throws IOException {
		if (!exists()) {
			return;
		}
        
        String urlString = this.getURL().toExternalForm().trim();
        int endOfJarUrl = urlString.indexOf("!/");
        int startOfJarUrl = (endOfJarUrl >= 0?4:0);
        
		if (endOfJarUrl < 0) {
			throw new IOException("Not a valid jar url: " + urlString);
		}
        
        URL jarFileURL = new URL(urlString.substring(startOfJarUrl, endOfJarUrl));
        String subEntryName = (endOfJarUrl+2 < urlString.length() ? urlString.substring(endOfJarUrl + 2) : null);
        boolean subEntryIsDir = (subEntryName != null && subEntryName.endsWith("/")?true:false);
      
		if (LOG.isDebugEnabled()) {
			LOG.debug("Extracting entry = " + subEntryName + " from jar " + jarFileURL);
		}
        URLConnection c = jarFileURL.openConnection();
        c.setUseCaches(false);
		try (InputStream is = c.getInputStream(); JarInputStream jin = new JarInputStream(is)) {
            JarEntry entry;
            boolean shouldExtract;
			while ((entry = jin.getNextJarEntry()) != null) {
                String entryName = entry.getName();
				if ((subEntryName != null) && (entryName.startsWith(subEntryName))) {
					if (!subEntryIsDir && subEntryName.length() + 1 == entryName.length() && entryName.endsWith("/")) {
						subEntryIsDir = true;
					}
					if (subEntryIsDir) {
                        entryName = entryName.substring(subEntryName.length());
						if (!entryName.equals("")) {
                            shouldExtract = true;
						} else {
							shouldExtract = false;
                        }
					} else {
						shouldExtract = true;
					}
				} else if ((subEntryName != null) && (!entryName.startsWith(subEntryName))) {
                    shouldExtract = false;
				} else {
                    shouldExtract =  true;
                }

				if (!shouldExtract) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Skipping entry: " + entryName);
					}
                    continue;
                }

                String dotCheck = entryName.replace('\\', '/');
                dotCheck = URIUtil.canonicalPath(dotCheck);
				if (dotCheck == null) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Invalid entry: " + entryName);
					}
                    continue;
                }

                File file=new File(directory,entryName);

                if (entry.isDirectory())                {
					if (!file.exists()) {
						file.mkdirs();
					}
				} else {
                    File dir = new File(file.getParent());
					if (!dir.exists()) {
						dir.mkdirs();
					}
					try (OutputStream fout = new FileOutputStream(file)) {
                        IO.copy(jin,fout);
                    }
					if (entry.getTime() >= 0) {
						file.setLastModified(entry.getTime());
					}
                }
            }

			if ((subEntryName == null)
					|| (subEntryName != null && subEntryName.equalsIgnoreCase("META-INF/MANIFEST.MF"))) {
                Manifest manifest = jin.getManifest();
				if (manifest != null) {
                    File metaInf = new File (directory, "META-INF");
                    metaInf.mkdir();
                    File f = new File(metaInf, "MANIFEST.MF");
					try (OutputStream fout = new FileOutputStream(f)) {
                        manifest.write(fout);
                    }
                }
            }
        }
    }   
    
	public static Resource newJarResource(Resource resource) throws IOException {
		if (resource instanceof JarResource) {
			return resource;
		}
        return Resource.newResource("jar:" + resource + "!/");
    }
}
