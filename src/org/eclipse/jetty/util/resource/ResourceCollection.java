package org.eclipse.jetty.util.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ResourceCollection extends Resource {// 将多个资源构成一个集合资源

    private static final Logger LOG = Log.getLogger(ResourceCollection.class);

	private Resource[] _resources;// 包含的所有资源

	// ******************************************************************************
	public ResourceCollection() {
		_resources = new Resource[0];// ???初始化为0个资源
    }
	public ResourceCollection(Resource... resources) {
        List<Resource> list = new ArrayList<Resource>();
		for (Resource r : resources) {
			if (r == null) {
                continue;
			}
			if (r instanceof ResourceCollection) {// 递归
				for (Resource r2 : ((ResourceCollection) r).getResources()) {
                    list.add(r2);
				}
			} else {
				list.add(r);
			}
        }
		_resources = list.toArray(new Resource[list.size()]);// 链表转换为数组
		for (Resource r : _resources) {
			if (!r.exists() || !r.isDirectory()) {// 表示不存在或者不是目录???
				throw new IllegalArgumentException(r + " is not an existing directory.");
			}
        }
    }
	public ResourceCollection(String[] resources) {
        _resources = new Resource[resources.length];
		try {
			for (int i = 0; i < resources.length; i++) {
				_resources[i] = Resource.newResource(resources[i]);// 生成资源
				if (!_resources[i].exists() || !_resources[i].isDirectory()) {
					throw new IllegalArgumentException(_resources[i] + " is not an existing directory.");
				}
            }
		} catch (IllegalArgumentException e) {
            throw e;
		} catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
	public ResourceCollection(String csvResources) {
        setResourcesAsCSV(csvResources);
    }
	// ******************************************************************************
	public Resource[] getResources() {// 获取该集合中的所有资源
        return _resources;
    }
	public void setResources(Resource[] resources) {
        _resources = resources != null ? resources : new Resource[0];
    }
	public void setResourcesAsCSV(String csvResources) {// CSV
		StringTokenizer tokenizer = new StringTokenizer(csvResources, ",;");
        int len = tokenizer.countTokens();
		if (len == 0) {
			throw new IllegalArgumentException("ResourceCollection@setResourcesAsCSV(String) "
					+ " argument must be a string containing one or more comma-separated resource strings.");
        }
        List<Resource> resources = new ArrayList<>();
		try {
			while (tokenizer.hasMoreTokens()) {
                Resource resource = Resource.newResource(tokenizer.nextToken().trim());
				if (!resource.exists() || !resource.isDirectory()) {
					LOG.warn(" !exist " + resource);
				} else {
					resources.add(resource);
				}
            }
		} catch (Exception e) {
            throw new RuntimeException(e);
        }
        _resources = resources.toArray(new Resource[resources.size()]);
    }
	protected Object findResource(String path) throws IOException, MalformedURLException {

		Resource resource = null;

		ArrayList<Resource> resources = null;

		int i = 0;

		for (; i < _resources.length; i++) {
            resource = _resources[i].addPath(path);  
			if (resource.exists()) {
				if (resource.isDirectory()) {
					break;
				}
                return resource;
            }
        }  
		for (i++; i < _resources.length; i++) {
            Resource r = _resources[i].addPath(path); 
			if (r.exists() && r.isDirectory()) {
				if (resource != null) {
                    resources = new ArrayList<Resource>();
                    resources.add(resource);
                }
                resources.add(r);
            }
        }

		if (resource != null) {
			return resource;
		}
		if (resources != null) {
			return resources;
		}
        return null;
    }

	// ******************************************************************************
	@Override
	public Resource addPath(String path) throws IOException, MalformedURLException {//

		if (_resources == null) {
			throw new IllegalStateException("*resources* not set.");
		}

		if (path == null) {
			throw new MalformedURLException();
		}

		if (path.length() == 0 || URIUtil.SLASH.equals(path)) {// 表示增加路径后依然是该资源
			return this;
		}

		Resource resource = null;

        ArrayList<Resource> resources = null;

		int i = 0;

		for (; i < _resources.length; i++) {

            resource = _resources[i].addPath(path);  

			if (resource.exists()) {

				if (resource.isDirectory()) {
					break;
				}

				return resource;// 存在
            }
		}

		for (i++; i < _resources.length; i++) {

            Resource r = _resources[i].addPath(path); 

			if (r.exists() && r.isDirectory()) {

				if (resources == null) {
					resources = new ArrayList<Resource>();
				}

				if (resource != null) {
                    resources.add(resource);
					resource = null;
                }

                resources.add(r);
            }
        }

		if (resource != null) {
			return resource;
		}
		if (resources != null) {
			return new ResourceCollection(resources.toArray(new Resource[resources.size()]));
		}
        return null;
    }
    @Override
	public boolean delete() throws SecurityException {
        throw new UnsupportedOperationException();
    }
    @Override
	public boolean exists() {
		if (_resources == null) {
			throw new IllegalStateException("*resources* not set.");
		}
        return true;
    }
    @Override
	public File getFile() throws IOException {
		if (_resources == null) {
            throw new IllegalStateException("*resources* not set.");
		}
		for (Resource r : _resources) {
            File f = r.getFile();
			if (f != null) {
				return f;
			}
        }
        return null;
    }
    @Override
	public InputStream getInputStream() throws IOException {
		if (_resources == null) {
			throw new IllegalStateException("*resources* not set.");
		}
		for (Resource r : _resources) {
            InputStream is = r.getInputStream();
			if (is != null) {
				return is;
			}
        }
        return null;
    }
    @Override 
	public ReadableByteChannel getReadableByteChannel() throws IOException {
		if (_resources == null) {
			throw new IllegalStateException("*resources* not set.");
		}
		for (Resource r : _resources) {
            ReadableByteChannel channel = r.getReadableByteChannel();
			if (channel != null) {
				return channel;
			}
        }
        return null;
    }
    @Override
	public String getName() {
		if (_resources == null) {
			throw new IllegalStateException("*resources* not set.");
		}
		for (Resource r : _resources) {
            String name = r.getName();
			if (name != null) {
				return name;
			}
        }
        return null;
    }
	@SuppressWarnings("deprecation")
	@Override
	public URL getURL() {
		if (_resources == null) {
			throw new IllegalStateException("*resources* not set.");
		}
		for (Resource r : _resources) {
            URL url = r.getURL();
			if (url != null) {
				return url;
			}
        }
        return null;
    }
    @Override
	public boolean isDirectory() {
		if (_resources == null) {
			throw new IllegalStateException("*resources* not set.");
		}
        return true;
    }
    @Override
	public long lastModified() {
		if (_resources == null) {
			throw new IllegalStateException("*resources* not set.");
		}
		for (Resource r : _resources) {
            long lm = r.lastModified();
			if (lm != -1) {
				return lm;
			}
        }
        return -1;
    }
	@Override
	public long length() {
        return -1;
    }    
    @Override
	public String[] list() {
		if (_resources == null) {
            throw new IllegalStateException("*resources* not set.");
		}
        HashSet<String> set = new HashSet<String>();
		for (Resource r : _resources) {
			for (String s : r.list()) {
				set.add(s);
			}
        }
        String[] result=set.toArray(new String[set.size()]);
        Arrays.sort(result);
        return result;
    }
    @Override
	public void close() {
		if (_resources == null) {
            throw new IllegalStateException("*resources* not set.");
		}
		for (Resource r : _resources) {
			r.close();
		}
    }
    @Override
	public boolean renameTo(Resource dest) throws SecurityException {
        throw new UnsupportedOperationException();
    }
    @Override
	public void copyTo(File destination) throws IOException {
		for (int r = _resources.length; r-- > 0;) {
			_resources[r].copyTo(destination);
		}
    }
    @Override
	public String toString() {
		if (_resources == null) {
            return "[]";
		}
        return String.valueOf(Arrays.asList(_resources));
    }
    @Override
	public boolean isContainedIn(Resource r) throws MalformedURLException {
        return false;
    }
}
