package org.eclipse.jetty.util.resource;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class Resource implements ResourceFactory, Closeable {//
	
    private static final Logger LOG = Log.getLogger(Resource.class);
	
	// ************************************************************************
	public static boolean __defaultUseCaches = true;// 创建资源时是否使用缓存

    public static void setDefaultUseCaches(boolean useCaches) {
        __defaultUseCaches = useCaches;
    }
	public static boolean getDefaultUseCaches() {
        return __defaultUseCaches;
    }
	// ************************************************************************

	volatile Object _associate;// ????

	// ******************************************************************************************************************************
	// 下面的静态方法都是根据指定路径创建资源类的
	public static Resource newResource(URI uri) throws MalformedURLException {// 表示根据URI来创建资源类
		return newResource(uri.toURL());
    }
	public static Resource newResource(URL url) {// 表示根据URL来创建资源类
        return newResource(url, __defaultUseCaches);
    }
	public static Resource newResource(String resource) throws MalformedURLException {
		return newResource(resource, __defaultUseCaches);
	}
	public static Resource newResource(File file) {
		return new PathResource(file.toPath());
	}
	public static Resource newClassPathResource(String resource) {
		return newClassPathResource(resource, true, false);
	}
	// 上面方法的底层实现
	static Resource newResource(URL url, boolean useCaches) {

		// 第二个参数表示使用缓存

		if (url == null) {
            return null;
		}

		String url_string = url.toExternalForm();// 获取对应的--file:/E:/Eclipse/workspace/JettyProject/bin/fileserver.xml

		if (url_string.startsWith("file:")) {// 协议字段

			try {

				return new PathResource(url);// 表示绝对路径

			} catch (Exception e) {

                LOG.warn(e.toString());
				LOG.debug(Log.EXCEPTION, e);

				return new BadResource(url, e.toString());
            }

		} else if (url_string.startsWith("jar:file:")) {

			return new JarFileResource(url, useCaches);//

		} else if (url_string.startsWith("jar:")) {

			return new JarResource(url, useCaches);//

        }

		return new URLResource(url, null, useCaches);//
    }
	public static Resource newResource(String resource, boolean useCaches) throws MalformedURLException {

		URL url = null;

		try {

			url = new URL(resource);

		} catch (MalformedURLException e) {

			if (!resource.startsWith("ftp:") && !resource.startsWith("file:") && !resource.startsWith("jar:")) {

				try {

					if (resource.startsWith("./")) {
						resource = resource.substring(2);
					}

					File file = new File(resource).getCanonicalFile();

                    return new PathResource(file);

				} catch (IOException e2) {

                    e.addSuppressed(e2);

                    throw e;
                }
			} else {

				LOG.warn("Bad Resource: " + resource);

                throw e;
            }
        }

        return newResource(url, useCaches);
    }
    public static Resource newSystemResource(String resource) throws IOException {

		URL url = null;

        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        if (loader != null) {

            try {

				url = loader.getResource(resource);// file:/E:/Eclipse/workspace/JettyProject/bin/fileserver.xml

				if (url == null && resource.startsWith("/")) {
					url = loader.getResource(resource.substring(1));
				}

            } catch (IllegalArgumentException e) {

                LOG.ignore(e);

                url = null;
            }
        }

		if (url == null) {

			loader = Resource.class.getClassLoader();

			if (loader != null) {

				url = loader.getResource(resource);

				if (url == null && resource.startsWith("/")) {

					url = loader.getResource(resource.substring(1));
				}
            }
        }

		if (url == null) {

			url = ClassLoader.getSystemResource(resource);

			if (url == null && resource.startsWith("/")) {

				url = ClassLoader.getSystemResource(resource.substring(1));

			}
        }

		if (url == null) {
            return null;
		}

        return newResource(url);
    }
	public static Resource newClassPathResource(String name, boolean useCaches, boolean checkParents) {

		URL url = Resource.class.getResource(name);

		if (url == null) {
			url = Loader.getResource(Resource.class, name);
		}

		if (url == null) {
			return null;
		}

		return newResource(url, useCaches);
    }
	// ******************************************************************************************************************************

	public static boolean isContainedIn(Resource r, Resource containingResource) throws MalformedURLException {
        return r.isContainedIn(containingResource);
    }
    @Override
	protected void finalize() {
        close();
    }

    @Deprecated
	public final void release() {
        close();
    }

	public abstract boolean isContainedIn(Resource r) throws MalformedURLException;

	public abstract boolean exists();// 判断该资源是否存在

	public abstract boolean isDirectory();// 判断该资源是否为目录
    public abstract long lastModified();
    public abstract long length();
    @Deprecated
    public abstract URL getURL();
	public abstract File getFile() throws IOException;
    public abstract String getName();
	public abstract InputStream getInputStream() throws IOException;// 获取对应资源的输入流
	public abstract ReadableByteChannel getReadableByteChannel() throws IOException;
	public abstract boolean delete() throws SecurityException;
	public abstract boolean renameTo(Resource dest) throws SecurityException;
	public abstract String[] list();//
	public abstract Resource addPath(String path) throws IOException, MalformedURLException;

	// ************************************************************************************
	// 实现的接口函数
    @Override
	public Resource getResource(String path) {
		try {
            return addPath(path);
		} catch (Exception e) {
            LOG.debug(e);
            return null;
        }
    }
	@Override
	public abstract void close();
	// ************************************************************************************

    @Deprecated
	public String encode(String uri) {
        return null;
    }
    @SuppressWarnings("javadoc")
	public Object getAssociate() {
        return _associate;
    }
    @SuppressWarnings("javadoc")
	public void setAssociate(Object o) {
		_associate = o;
    }
	public boolean isAlias() {
		return getAlias() != null;
    }
	public URI getAlias() {
        return null;
    }
	public String getListHTML(String base, boolean parent) throws IOException {
		base = URIUtil.canonicalPath(base);
		if (base == null || !isDirectory()) {
			return null;
		}
        String[] ls = list();
		if (ls == null) {
			return null;
		}
        Arrays.sort(ls);
        String decodedBase = URIUtil.decodePath(base);
        String title = "Directory: "+deTag(decodedBase);

		StringBuilder buf = new StringBuilder(4096);
        buf.append("<HTML><HEAD>");
        buf.append("<LINK HREF=\"").append("jetty-dir.css").append("\" REL=\"stylesheet\" TYPE=\"text/css\"/><TITLE>");
        buf.append(title);
        buf.append("</TITLE></HEAD><BODY>\n<H1>");
        buf.append(title);
        buf.append("</H1>\n<TABLE BORDER=0>\n");
		if (parent) {
            buf.append("<TR><TD><A HREF=\"");
            buf.append(URIUtil.addPaths(base,"../"));
            buf.append("\">Parent Directory</A></TD><TD></TD><TD></TD></TR>\n");
        }
        String encodedBase = hrefEncodeURI(base);
		DateFormat dfmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
		for (int i = 0; i < ls.length; i++) {
            Resource item = addPath(ls[i]);
            buf.append("\n<TR><TD><A HREF=\"");
			String path = URIUtil.addPaths(encodedBase, URIUtil.encodePath(ls[i]));
            buf.append(path);
			if (item.isDirectory() && !path.endsWith("/")) {
				buf.append(URIUtil.SLASH);
			}
            // URIUtil.encodePath(buf,path);
            buf.append("\">");
            buf.append(deTag(ls[i]));
            buf.append("&nbsp;");
            buf.append("</A></TD><TD ALIGN=right>");
            buf.append(item.length());
            buf.append(" bytes&nbsp;</TD><TD>");
            buf.append(dfmt.format(new Date(item.lastModified())));
            buf.append("</TD></TR>");
        }
        buf.append("</TABLE>\n");
        buf.append("</BODY></HTML>\n");
        return buf.toString();
    }
	private static String hrefEncodeURI(String raw) {
        StringBuffer buf = null;
        loop:
		for (int i = 0; i < raw.length(); i++) {
            char c=raw.charAt(i);
			switch (c) {
                case '\'':
                case '"':
                case '<':
                case '>':
				buf = new StringBuffer(raw.length() << 1);
                    break loop;
            }
        }
		if (buf == null) {
            return raw;
		}
		for (int i = 0; i < raw.length(); i++) {
            char c=raw.charAt(i);       
			switch (c) {
              case '"':
                  buf.append("%22");
                  continue;
              case '\'':
                  buf.append("%27");
                  continue;
              case '<':
                  buf.append("%3C");
                  continue;
              case '>':
                  buf.append("%3E");
                  continue;
              default:
                  buf.append(c);
                  continue;
            }
        }
        return buf.toString();
    }
	private static String deTag(String raw) {
        return StringUtil.sanitizeXmlString(raw);
    }
	public void writeTo(OutputStream out, long start, long count) throws IOException {
		try (InputStream in = getInputStream()) {//
			in.skip(start);//
			if (count < 0) {
				IO.copy(in, out);
			} else {
				IO.copy(in, out, count);
			}
        }
	}

	public void copyTo(File destination) throws IOException {//
		if (destination.exists()) {
            throw new IllegalArgumentException(destination + " exists");
		}
		try (OutputStream out = new FileOutputStream(destination)) {
			writeTo(out, 0, -1);
        }
    }
	public String getWeakETag() {
        return getWeakETag("");
    }
	public String getWeakETag(String suffix) {
		try {
            StringBuilder b = new StringBuilder(32);
			b.append("W/\"");// --W/"
			String name = getName();//
			int length = name.length();
			long lhash = 0;
			for (int i = 0; i < length; i++) {
				lhash = 31 * lhash + name.charAt(i);
			}
			B64Code.encode(lastModified() ^ lhash, b);//
			B64Code.encode(length() ^ lhash, b);//
            b.append(suffix);
            b.append('"');
            return b.toString();
		} catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
	public Collection<Resource> getAllResources() {
		try {
			ArrayList<Resource> deep = new ArrayList<>();
			{
				String[] list = list();//
				if (list != null) {
					for (String i : list) {
						Resource r = addPath(i);//
						if (r.isDirectory()) {//
							deep.addAll(r.getAllResources());//
						} else {
                            deep.add(r);
						}
                    }
                }
            }
            return deep;
		} catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

	public static URL toURL(File file) throws MalformedURLException {//
        return file.toURI().toURL();
    }
	public URI getURI() {
		try {
			return getURL().toURI();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
