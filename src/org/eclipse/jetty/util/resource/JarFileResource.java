package org.eclipse.jetty.util.resource;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

class JarFileResource extends JarResource {

    private static final Logger LOG = Log.getLogger(JarFileResource.class);

    private JarFile _jarFile;
    private File _file;
    private String[] _list;
    private JarEntry _entry;
    private boolean _directory;
    private String _jarUrl;
    private String _path;
    private boolean _exists;
    
	protected JarFileResource(URL url) {
        super(url);
    }

	protected JarFileResource(URL url, boolean useCaches) {// jar:file:
        super(url, useCaches);
    }   

    @Override
	public synchronized void close() {
        _exists=false;
        _list=null;
        _entry=null;
        _file=null;
        //if the jvm is not doing url caching, then the JarFiles will not be cached either,
        //and so they are safe to close
		if (!getUseCaches()) {
			if (_jarFile != null) {
				try {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Closing JarFile " + _jarFile.getName());
					}
                    _jarFile.close();
				} catch (IOException ioe) {
                    LOG.ignore(ioe);
                }
            }
        }
        _jarFile=null;
        super.close();
    }
    
    @Override
	protected synchronized boolean checkConnection() {
		try {
            super.checkConnection();
		} finally {
			if (_jarConnection == null) {
                _entry=null;
                _file=null;
                _jarFile=null;
                _list=null;
            }
        }
        return _jarFile!=null;
    }


    /* ------------------------------------------------------------ */
    @Override
	protected synchronized void newConnection() throws IOException {
        super.newConnection();
        
        _entry=null;
        _file=null;
        _jarFile=null;
        _list=null;
        
        int sep = _urlString.lastIndexOf("!/");
        _jarUrl=_urlString.substring(0,sep+2);
        _path=_urlString.substring(sep+2);
        if (_path.length()==0)
            _path=null;   
        _jarFile=_jarConnection.getJarFile();
        _file=new File(_jarFile.getName());
    }
    @Override
	public boolean exists() {
		if (_exists) {
            return true;
		}
		if (_urlString.endsWith("!/")) {
            String file_url=_urlString.substring(4,_urlString.length()-2);
            try{return newResource(file_url).exists();}
            catch(Exception e) {LOG.ignore(e); return false;}
        }
        
        boolean check=checkConnection();
        
        // Is this a root URL?
		if (_jarUrl != null && _path == null) {
            // Then if it exists it is a directory
            _directory=check;
            return true;
		} else {
            // Can we find a file for it?
            boolean close_jar_file= false;
            JarFile jar_file=null;
			if (check) {
                // Yes
                jar_file=_jarFile;
			} else {
                // No - so lets look if the root entry exists.
				try {
					JarURLConnection c = (JarURLConnection) ((new URL(_jarUrl)).openConnection());
                    c.setUseCaches(getUseCaches());
                    jar_file=c.getJarFile();
                    close_jar_file = !getUseCaches();
				} catch (Exception e) {
                    LOG.ignore(e);
                }
            }

            // Do we need to look more closely?
			if (jar_file != null && _entry == null && !_directory) {
                // OK - we have a JarFile, lets look for the entry
                JarEntry entry = jar_file.getJarEntry(_path);
				if (entry == null) {
                    // the entry does not exist
                    _exists = false;
				} else if (entry.isDirectory()) {
                    _directory = true;
                    _entry = entry;
				} else {
                    // Let's confirm is a file
                    JarEntry directory = jar_file.getJarEntry(_path + '/');
					if (directory != null) {
                        _directory = true;
                        _entry = directory;
					} else {
						_directory = false;
                      _entry = entry;
                    }
                }
            }

			if (close_jar_file && jar_file != null) {
				try {
                    jar_file.close();
				} catch (IOException ioe) {
                    LOG.ignore(ioe);
                }
            }
        }
        
        _exists= ( _directory || _entry!=null);
        return _exists;
    }
    @Override
	public boolean isDirectory() {
        return _urlString.endsWith("/") || exists() && _directory;
    }
    @Override
	public long lastModified() {
		if (checkConnection() && _file != null) {
			if (exists() && _entry != null) {
                return _entry.getTime();
			}
            return _file.lastModified();
        }
        return -1;
    }
    @Override
	public synchronized String[] list() {
		if (isDirectory() && _list == null) {
            List<String> list = null;
			try {
                list = listEntries();
			} catch (Exception e) {

                LOG.warn("Retrying list:"+e);
                LOG.debug(e);
                close();
                list = listEntries();
            }

			if (list != null) {
                _list=new String[list.size()];
                list.toArray(_list);
            }  
        }
        return _list;
    }

	private List<String> listEntries() {
        checkConnection();
        
        ArrayList<String> list = new ArrayList<String>(32);
        JarFile jarFile=_jarFile;
		if (jarFile == null) {
			try {
                JarURLConnection jc=(JarURLConnection)((new URL(_jarUrl)).openConnection());
                jc.setUseCaches(getUseCaches());
                jarFile=jc.getJarFile();
			} catch (Exception e) {
                e.printStackTrace();
                 LOG.ignore(e);
            }
			if (jarFile == null) {
				throw new IllegalStateException();
			}
        }
        
        Enumeration<JarEntry> e=jarFile.entries();
        String dir=_urlString.substring(_urlString.lastIndexOf("!/")+2);
		while (e.hasMoreElements()) {
            JarEntry entry = e.nextElement();               
            String name=entry.getName().replace('\\','/');               
			if (!name.startsWith(dir) || name.length() == dir.length()) {
                continue;
            }
            String listName=name.substring(dir.length());               
            int dash=listName.indexOf('/');
			if (dash >= 0) {
                //when listing jar:file urls, you get back one
                //entry for the dir itself, which we ignore
                if (dash==0 && listName.length()==1)
                    continue;
                //when listing jar:file urls, all files and
                //subdirs have a leading /, which we remove
                if (dash==0)
                    listName=listName.substring(dash+1, listName.length());
                else
                    listName=listName.substring(0,dash+1);
                
                if (list.contains(listName))
                    continue;
            }
            
            list.add(listName);
        }
		return list;
    }
    @Override
	public long length() {
        if (isDirectory())
            return -1;

        if (_entry!=null)
            return _entry.getSize();
        
        return -1;
    }

	public static Resource getNonCachingResource(Resource resource) {
        if (!(resource instanceof JarFileResource))
            return resource;
        
        JarFileResource oldResource = (JarFileResource)resource;
        
        JarFileResource newResource = new JarFileResource(oldResource.getURL(), false);
        return newResource;
    }
    @Override
	public boolean isContainedIn(Resource resource) throws MalformedURLException {
        String string = _urlString;
        int index = string.lastIndexOf("!/");
        if (index > 0)
            string = string.substring(0,index);
        if (string.startsWith("jar:"))
            string = string.substring(4);
        URL url = new URL(string);
        return url.sameFile(resource.getURI().toURL());     
    }
}
