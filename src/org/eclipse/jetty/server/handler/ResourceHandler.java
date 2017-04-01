package org.eclipse.jetty.server.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import javax.servlet.AsyncContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

public class ResourceHandler extends HandlerWrapper implements ResourceFactory {

    private static final Logger LOG = Log.getLogger(ResourceHandler.class);

    ContextHandler _context;
    Resource _baseResource;
    Resource _defaultStylesheet;
    Resource _stylesheet;
	String[] _welcomeFiles = { "index.html" };
    MimeTypes _mimeTypes;
    String _cacheControl;
    boolean _directory;
    boolean _gzip;
    boolean _etags;
	int _minMemoryMappedContentLength = 0;
	int _minAsyncContentLength = 16 * 1024;

	// ***************************************************************************************************
	public ResourceHandler() {

    }
	// ***************************************************************************************************

	public MimeTypes getMimeTypes() {
        return _mimeTypes;
    }
	public void setMimeTypes(MimeTypes mimeTypes) {
        _mimeTypes = mimeTypes;
    }
	public boolean isDirectoriesListed() {
        return _directory;
    }
	public void setDirectoriesListed(boolean directory) {
        _directory = directory;
    }
	public int getMinMemoryMappedContentLength() {
        return _minMemoryMappedContentLength;
    }
	public void setMinMemoryMappedContentLength(int minMemoryMappedFileSize) {
        _minMemoryMappedContentLength = minMemoryMappedFileSize;
    }
	public int getMinAsyncContentLength() {
        return _minAsyncContentLength;
    }
	public void setMinAsyncContentLength(int minAsyncContentLength) {
        _minAsyncContentLength = minAsyncContentLength;
    }
	public boolean isEtags() {
        return _etags;
    }
	public void setEtags(boolean etags) {
        _etags = etags;
    }

	// ***************************************************************************************************
    @Override
	public void doStart() throws Exception {
        Context scontext = ContextHandler.getCurrentContext();
		_context = (scontext == null ? null : scontext.getContextHandler());
		_mimeTypes = _context == null ? new MimeTypes() : _context.getMimeTypes();
        super.doStart();
    }

	public Resource getBaseResource() {
		if (_baseResource == null) {
			return null;
		}
        return _baseResource;
    }
	public String getResourceBase() {
		if (_baseResource == null) {
			return null;
		}
        return _baseResource.toString();
    }
	public void setBaseResource(Resource base) {
		_baseResource = base;
    }

	public void setResourceBase(String resourceBase) {
		try {
            setBaseResource(Resource.newResource(resourceBase));
		} catch (Exception e) {
            LOG.warn(e.toString());
            LOG.debug(e);
            throw new IllegalArgumentException(resourceBase);
        }
    }

	public Resource getStylesheet() {
		if (_stylesheet != null) {
            return _stylesheet;
		} else {
			if (_defaultStylesheet == null) {
                _defaultStylesheet =  Resource.newResource(this.getClass().getResource("/jetty-dir.css"));
            }
            return _defaultStylesheet;
        }
    }

	public void setStylesheet(String stylesheet) {
		try {
            _stylesheet = Resource.newResource(stylesheet);
			if (!_stylesheet.exists()) {
                LOG.warn("unable to find custom stylesheet: " + stylesheet);
                _stylesheet = null;
            }
		} catch (Exception e) {
            LOG.warn(e.toString());
            LOG.debug(e);
            throw new IllegalArgumentException(stylesheet);
        }
    }

	public String getCacheControl() {
        return _cacheControl;
    }

	public void setCacheControl(String cacheControl) {
        _cacheControl=cacheControl;
    }

    @Override
	public Resource getResource(String path) {
		if (path == null || !path.startsWith("/")) {
			return null;
		}
		try {
            Resource base = _baseResource;
			if (base == null) {
				if (_context == null) {
					return null;
				}
                return _context.getResource(path);
            }

            path=URIUtil.canonicalPath(path);
            Resource r = base.addPath(path);
			if (r != null && r.isAlias() && (_context == null || !_context.checkAlias(path, r))) {
                return null;
            }
            return r;
		} catch (Exception e) {
            LOG.debug(e);
        }

        return null;
    }

	protected Resource getResource(HttpServletRequest request) throws MalformedURLException {
        String servletPath;
        String pathInfo;
        Boolean included = request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null;
		if (included != null && included.booleanValue()) {
            servletPath = (String)request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
            pathInfo = (String)request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);

			if (servletPath == null && pathInfo == null) {
                servletPath = request.getServletPath();
                pathInfo = request.getPathInfo();
            }
		} else {
            servletPath = request.getServletPath();
            pathInfo = request.getPathInfo();
        }

        String pathInContext=URIUtil.addPaths(servletPath,pathInfo);
        return getResource(pathInContext);
    }

	public String[] getWelcomeFiles() {
        return _welcomeFiles;
    }

	public void setWelcomeFiles(String[] welcomeFiles) {
		_welcomeFiles = welcomeFiles;
    }

	protected Resource getWelcome(Resource directory) throws MalformedURLException, IOException {
		for (int i = 0; i < _welcomeFiles.length; i++) {
			Resource welcome = directory.addPath(_welcomeFiles[i]);
			if (welcome.exists() && !welcome.isDirectory()) {
				return welcome;
			}
        }

        return null;
    }
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
		if (baseRequest.isHandled()) {
			return;
		}

        boolean skipContentBody = false;

		if (!HttpMethod.GET.is(request.getMethod())) {
			if (!HttpMethod.HEAD.is(request.getMethod())) {
                super.handle(target, baseRequest, request, response);
                return;
            }
            skipContentBody = true;
        }

        Resource resource = getResource(request);

		if (resource == null || !resource.exists()) {
			if (target.endsWith("/jetty-dir.css")) {
                resource = getStylesheet();
				if (resource == null) {
					return;
				}
                response.setContentType("text/css");
			} else {
                super.handle(target, baseRequest, request, response);
                return;
            }
        }

        baseRequest.setHandled(true);

		if (resource.isDirectory()) {
            String pathInfo = request.getPathInfo();
            boolean endsWithSlash=(pathInfo==null?request.getServletPath():pathInfo).endsWith(URIUtil.SLASH);
			if (!endsWithSlash) {
                response.sendRedirect(response.encodeRedirectURL(URIUtil.addPaths(request.getRequestURI(),URIUtil.SLASH)));
                return;
            }

            Resource welcome=getWelcome(resource);
			if (welcome != null && welcome.exists()) {
				resource = welcome;
			} else {
				doDirectory(request, response, resource);
                baseRequest.setHandled(true);
                return;
            }
        }

        // Handle ETAGS
        long last_modified=resource.lastModified();
        String etag=null;
        if (_etags)
        {
            // simple handling of only a single etag
            String ifnm = request.getHeader(HttpHeader.IF_NONE_MATCH.asString());
            etag=resource.getWeakETag();
            if (ifnm!=null && resource!=null && ifnm.equals(etag))
            {
                response.setStatus(HttpStatus.NOT_MODIFIED_304);
                baseRequest.getResponse().getHttpFields().put(HttpHeader.ETAG,etag);
                return;
            }
        }
        
        // Handle if modified since 
		if (last_modified > 0) {
            long if_modified=request.getDateHeader(HttpHeader.IF_MODIFIED_SINCE.asString());
			if (if_modified > 0 && last_modified / 1000 <= if_modified / 1000) {
                response.setStatus(HttpStatus.NOT_MODIFIED_304);
                return;
            }
        }

        // set the headers
        String mime=_mimeTypes.getMimeByExtension(resource.toString());
		if (mime == null) {
			mime = _mimeTypes.getMimeByExtension(request.getPathInfo());
		}
        doResponseHeaders(response,resource,mime);
		if (_etags) {
			baseRequest.getResponse().getHttpFields().put(HttpHeader.ETAG, etag);
		}
		if (last_modified > 0) {
			response.setDateHeader(HttpHeader.LAST_MODIFIED.asString(), last_modified);
		}
        
		if (skipContentBody) {
			return;
		}
        
        // Send the content
        OutputStream out =null;
        try {out = response.getOutputStream();}
        catch(IllegalStateException e) {out = new WriterOutputStream(response.getWriter());}

        // Has the output been wrapped
		if (!(out instanceof HttpOutput)) { // Write content via wrapped output
            resource.writeTo(out,0,resource.length());
		} else {
            // select async by size
            int min_async_size=_minAsyncContentLength==0?response.getBufferSize():_minAsyncContentLength;
            
			if (request.isAsyncSupported() && min_async_size > 0 && resource.length() >= min_async_size) {
                final AsyncContext async = request.startAsync();
                async.setTimeout(0);
				Callback callback = new Callback() {
                    @Override
					public void succeeded() {
                        async.complete();
                    }

                    @Override
					public void failed(Throwable x) {
                        LOG.warn(x.toString());
                        LOG.debug(x);
                        async.complete();
                    }   
                };

                // Can we use a memory mapped file?
                if (_minMemoryMappedContentLength>0 && 
                    resource.length()>_minMemoryMappedContentLength &&
                    resource.length()<Integer.MAX_VALUE &&
						resource instanceof PathResource) {
                    ByteBuffer buffer = BufferUtil.toMappedBuffer(resource.getFile());
                    ((HttpOutput)out).sendContent(buffer,callback);
				} else { // Do a blocking write of a channel (if available) or
							// input stream

                    // Close of the channel/inputstream is done by the async sendContent
                    ReadableByteChannel channel= resource.getReadableByteChannel();
					if (channel != null) {
						((HttpOutput) out).sendContent(channel, callback);
					} else {
						((HttpOutput) out).sendContent(resource.getInputStream(), callback);
					}
                }
			} else {
				if (_minMemoryMappedContentLength > 0 && resource.length() > _minMemoryMappedContentLength
						&& resource instanceof PathResource) {
                    ByteBuffer buffer = BufferUtil.toMappedBuffer(resource.getFile());
                    ((HttpOutput)out).sendContent(buffer);
				} else {
                    ReadableByteChannel channel= resource.getReadableByteChannel();
					if (channel != null) {
						((HttpOutput) out).sendContent(channel);
					} else {
						((HttpOutput) out).sendContent(resource.getInputStream());
					}
                }
            }
        }
    }

    /* ------------------------------------------------------------ */
	protected void doDirectory(HttpServletRequest request, HttpServletResponse response, Resource resource)
			throws IOException {
		if (_directory) {
			String listing = resource.getListHTML(request.getRequestURI(), request.getPathInfo().lastIndexOf("/") > 0);
            response.setContentType("text/html;charset=utf-8");
            response.getWriter().println(listing);
		} else {
			response.sendError(HttpStatus.FORBIDDEN_403);
		}
    }
	protected void doResponseHeaders(HttpServletResponse response, Resource resource, String mimeType) {
		if (mimeType != null) {
			response.setContentType(mimeType);
		}

        long length=resource.length();

		if (response instanceof Response) {
            HttpFields fields = ((Response)response).getHttpFields();

			if (length > 0) {
				((Response) response).setLongContentLength(length);
			}

			if (_cacheControl != null) {
				fields.put(HttpHeader.CACHE_CONTROL, _cacheControl);
			}
		} else {
			if (length > Integer.MAX_VALUE) {
				response.setHeader(HttpHeader.CONTENT_LENGTH.asString(), Long.toString(length));
			} else if (length > 0) {
				response.setContentLength((int) length);
			}

			if (_cacheControl != null) {
				response.setHeader(HttpHeader.CACHE_CONTROL.asString(), _cacheControl);
			}
        }
    }
}
