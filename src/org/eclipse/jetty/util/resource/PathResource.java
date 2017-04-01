package org.eclipse.jetty.util.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class PathResource extends Resource {

    private static final Logger LOG = Log.getLogger(PathResource.class);

    private final static LinkOption NO_FOLLOW_LINKS[] = new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
    private final static LinkOption FOLLOW_LINKS[] = new LinkOption[] {};
    
	private final Path path;// 绝对路径
	private final Path alias;// 当上面的路径无效时检测是否使用了别名
	private final URI uri;// 表示对应的URI路径
    

	// ******************************************************************************************************************************
	public PathResource(File file) {
        this(file.toPath());
    }
	public PathResource(Path path) {

        this.path = path.toAbsolutePath();

		assertValidPath(path);// 控制字符检测

        this.uri = this.path.toUri();

        this.alias = checkAliasPath();
    }
	private PathResource(PathResource parent, String childPath) throws MalformedURLException {
        this.path = parent.path.getFileSystem().getPath(parent.path.toString(), childPath);
		if (isDirectory() && !childPath.endsWith("/")) {
			childPath += "/";
		}
		this.uri = URIUtil.addDecodedPath(parent.uri, childPath);
        this.alias = checkAliasPath();
    }
	public PathResource(URI uri) throws IOException {

		if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("not an absolute uri");
        }

		if (!uri.getScheme().equalsIgnoreCase("file")) {
            throw new IllegalArgumentException("not file: scheme");
        }

        Path path;

		try {

			path = new File(uri).toPath();// E:/Eclipse/workspace/JettyProject/bin/fileserver.xml

		} catch (InvalidPathException e) {
            throw e;
		} catch (IllegalArgumentException e) {
            throw e;
		} catch (Exception e) {
            LOG.ignore(e);
            throw new IOException("Unable to build Path from: " + uri,e);
        }

		this.path = path.toAbsolutePath();// E:\Eclipse\workspace\JettyProject\bin\fileserver.xml
		this.uri = path.toUri();// file:///E:/Eclipse/workspace/JettyProject/bin/fileserver.xml
		this.alias = checkAliasPath();// null
    }
	public PathResource(URL url) throws IOException, URISyntaxException {// 处理URL路径以FILE开始的资源
        this(url.toURI());
    }
	// ******************************************************************************************************************************
	private void assertValidPath(Path path) {

		String str = path.toString();

		int idx = StringUtil.indexOfControlChars(str);

		if (idx >= 0) {
			throw new InvalidPathException(str, "invalid character at index " + idx);
		}
	}
	public Path getPath() {
		return path;
	}
	public Path getAliasPath() {
		return this.alias;
	}
	private final Path checkAliasPath() {

		Path abs = path;

		if (!URIUtil.equalsIgnoreEncodings(uri, path.toUri())) {
			return new File(uri).toPath().toAbsolutePath();
		}

		if (!abs.isAbsolute()) {
			abs = path.toAbsolutePath();
		}

		try {

			if (Files.isSymbolicLink(path)) {
				return Files.readSymbolicLink(path);
			}

			if (Files.exists(path)) {

				Path real = abs.toRealPath(FOLLOW_LINKS);

				int absCount = abs.getNameCount();
				int realCount = real.getNameCount();

				if (absCount != realCount) {
					return real;
				}

				for (int i = realCount - 1; i >= 0; i--) {
					if (!abs.getName(i).toString().equals(real.getName(i).toString())) {
						return real;
					}
				}
			}
		} catch (IOException e) {
			LOG.ignore(e);
		} catch (Exception e) {
			LOG.warn("bad alias ({} {}) for {}", e.getClass().getName(), e.getMessage(), path);
		}
		return null;
	}
	// ******************************************************************************************************************************
	// 下面是实现函数
    @Override
	public Resource addPath(final String subpath) throws IOException, MalformedURLException {
        String cpath = URIUtil.canonicalPath(subpath);

		if ((cpath == null) || (cpath.length() == 0)) {
			throw new MalformedURLException(subpath);
		}

		if ("/".equals(cpath)) {
			return this;
		}
        return new PathResource(this, subpath);
    }
    @Override
	public void close() {
    }

    @Override
	public boolean delete() throws SecurityException {
		try {
            return Files.deleteIfExists(path);
		} catch (IOException e) {
            LOG.ignore(e);
            return false;
        }
    }

    @Override
	public boolean equals(Object obj) {
		if (this == obj) {
            return true;
        }
		if (obj == null) {
            return false;
        }
		if (getClass() != obj.getClass()) {
            return false;
        }
        PathResource other = (PathResource)obj;
		if (path == null) {
			if (other.path != null) {
                return false;
            }
		} else if (!path.equals(other.path)) {
            return false;
        }
        return true;
    }

    @Override
	public boolean exists() {
        return Files.exists(path,NO_FOLLOW_LINKS);
    }

    @Override
	public File getFile() throws IOException {
        return path.toFile();
    }

    @Override
	public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path,StandardOpenOption.READ);
    }

    @Override
	public String getName() {
        return path.toAbsolutePath().toString();
    }

    @Override
	public ReadableByteChannel getReadableByteChannel() throws IOException {
        return FileChannel.open(path,StandardOpenOption.READ);
    }

    @Override
	public URI getURI() {
        return this.uri;
    }

    @Override
	public URL getURL() {
		try {
            return path.toUri().toURL();
		} catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
	public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((path == null)?0:path.hashCode());
        return result;
    }

    @Override
	public boolean isContainedIn(Resource r) throws MalformedURLException {
        return false;
    }

    @Override
	public boolean isDirectory() {
        return Files.isDirectory(path,FOLLOW_LINKS);
    }

    @Override
	public long lastModified() {
		try {
            FileTime ft = Files.getLastModifiedTime(path,FOLLOW_LINKS);
            return ft.toMillis();
		} catch (IOException e) {
            LOG.ignore(e);
            return 0;
        }
    }

    @Override
	public long length() {
		try {
            return Files.size(path);
		} catch (IOException e) {
            return 0L;
        }
    }

    @Override
	public boolean isAlias() {
        return this.alias!=null;
    }

    @Override
	public URI getAlias() {
        return this.alias==null?null:this.alias.toUri();
    }
    @Override
	public String[] list() {
		try (DirectoryStream<Path> dir = Files.newDirectoryStream(path)) {
            List<String> entries = new ArrayList<>();
			for (Path entry : dir) {
                String name = entry.getFileName().toString();

				if (Files.isDirectory(entry)) {
                    name += "/";
                }
                entries.add(name);
            }
            int size = entries.size();
            return entries.toArray(new String[size]);
		} catch (DirectoryIteratorException e) {
            LOG.debug(e);
		} catch (IOException e) {
            LOG.debug(e);
        }
        return null;
    }
    @Override
	public boolean renameTo(Resource dest) throws SecurityException {
		if (dest instanceof PathResource) {
            PathResource destRes = (PathResource)dest;
			try {
                Path result = Files.move(path,destRes.path);
                return Files.exists(result,NO_FOLLOW_LINKS);
			} catch (IOException e) {
                LOG.ignore(e);
                return false;
            }
		} else {
            return false;
        }
    }
    @Override
	public void copyTo(File destination) throws IOException {
		if (isDirectory()) {
            IO.copyDir(this.path.toFile(),destination);
		} else {
            Files.copy(this.path,destination.toPath());
        }
    }
    @Override
	public String toString() {
        return this.uri.toASCIIString();
    }
	// ******************************************************************************************************************************
}