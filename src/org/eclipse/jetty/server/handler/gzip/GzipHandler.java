package org.eclipse.jetty.server.handler.gzip;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.zip.Deflater;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.GzipHttpContent;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.pathmap.PathSpecSet;
import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.IncludeExclude;
import org.eclipse.jetty.util.RegexSet;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class GzipHandler extends HandlerWrapper implements GzipFactory {

	private static final Logger LOG = Log.getLogger(GzipHandler.class);

    public final static String GZIP = "gzip";
    public final static String DEFLATE = "deflate";
    public final static int DEFAULT_MIN_GZIP_SIZE=16;
    private int _minGzipSize=DEFAULT_MIN_GZIP_SIZE;
    private int _compressionLevel=Deflater.DEFAULT_COMPRESSION;
    private boolean _checkGzExists = true;
    private boolean _syncFlush = false;

    // non-static, as other GzipHandler instances may have different configurations
    private final ThreadLocal<Deflater> _deflater = new ThreadLocal<>();

    private final IncludeExclude<String> _agentPatterns=new IncludeExclude<>(RegexSet.class);
    private final IncludeExclude<String> _methods = new IncludeExclude<>();
    private final IncludeExclude<String> _paths = new IncludeExclude<>(PathSpecSet.class);
    private final IncludeExclude<String> _mimeTypes = new IncludeExclude<>();

    private HttpField _vary;

	public GzipHandler() {
        _methods.include(HttpMethod.GET.asString());
		for (String type : MimeTypes.getKnownMimeTypes()) {
			if ("image/svg+xml".equals(type)) {
                _paths.exclude("*.svgz");
			} else if (type.startsWith("image/") ||
                type.startsWith("audio/")||
					type.startsWith("video/")) {
                _mimeTypes.exclude(type);
			}
        }
        _mimeTypes.exclude("application/compress");
        _mimeTypes.exclude("application/zip");
        _mimeTypes.exclude("application/gzip");
        _mimeTypes.exclude("application/bzip2");
        _mimeTypes.exclude("application/x-rar-compressed");
        LOG.debug("{} mime types {}",this,_mimeTypes);
        _agentPatterns.exclude(".*MSIE 6.0.*");
    }

	public void addExcludedAgentPatterns(String... patterns) {
        _agentPatterns.exclude(patterns);
    }

	public void addExcludedMethods(String... methods) {
		for (String m : methods) {
            _methods.exclude(m);
		}
    }

	public void addExcludedMimeTypes(String... types) {
		for (String t : types) {
            _mimeTypes.exclude(StringUtil.csvSplit(t));
		}
    }

	public void addExcludedPaths(String... pathspecs) {
		for (String p : pathspecs) {
            _paths.exclude(StringUtil.csvSplit(p));
		}
    }

	public void addIncludedAgentPatterns(String... patterns) {
        _agentPatterns.include(patterns);
    }

	public void addIncludedMethods(String... methods) {
		for (String m : methods) {
            _methods.include(m);
		}
    }

	public boolean isSyncFlush() {
        return _syncFlush;
    }

	public void setSyncFlush(boolean syncFlush) {
        _syncFlush = syncFlush;
    }

	public void addIncludedMimeTypes(String... types) {
		for (String t : types) {
            _mimeTypes.include(StringUtil.csvSplit(t));
		}
    }

	public void addIncludedPaths(String... pathspecs) {
		for (String p : pathspecs) {
            _paths.include(StringUtil.csvSplit(p));
		}
    }
    @Override
	protected void doStart() throws Exception {
		_vary = (_agentPatterns.size() > 0) ? GzipHttpOutputInterceptor.VARY_ACCEPT_ENCODING_USER_AGENT
				: GzipHttpOutputInterceptor.VARY_ACCEPT_ENCODING;
        super.doStart();
    }

	public boolean getCheckGzExists() {
        return _checkGzExists;
    }
	public int getCompressionLevel() {
        return _compressionLevel;
    }

	// *******************************************************************
    @Override
	public Deflater getDeflater(Request request, long content_length) {
        String ua = request.getHttpFields().get(HttpHeader.USER_AGENT);
		if (ua != null && !isAgentGzipable(ua)) {
            LOG.debug("{} excluded user agent {}",this,request);
            return null;
        }
		if (content_length >= 0 && content_length < _minGzipSize) {
            LOG.debug("{} excluded minGzipSize {}",this,request);
            return null;
        }
        HttpField accept = request.getHttpFields().getField(HttpHeader.ACCEPT_ENCODING);
		if (accept == null) {
            LOG.debug("{} excluded !accept {}",this,request);
            return null;
        }
        boolean gzip = accept.contains("gzip");
		if (!gzip) {
            LOG.debug("{} excluded not gzip accept {}",this,request);
            return null;
        }
        Deflater df = _deflater.get();
		if (df == null) {
            df=new Deflater(_compressionLevel,true);
		} else {
            _deflater.set(null);
		}
        return df;
    }
	@Override
	public boolean isMimeTypeGzipable(String mimetype) {
		return _mimeTypes.matches(mimetype);
	}
	@Override
	public void recycle(Deflater deflater) {
		deflater.reset();
		if (_deflater.get() == null) {
			_deflater.set(deflater);
		}
	}
	// *******************************************************************

	public String[] getExcludedAgentPatterns() {
        Set<String> excluded=_agentPatterns.getExcluded();
        return excluded.toArray(new String[excluded.size()]);
    }

	public String[] getExcludedMethods() {
        Set<String> excluded=_methods.getExcluded();
        return excluded.toArray(new String[excluded.size()]);
    }

	public String[] getExcludedMimeTypes() {
        Set<String> excluded=_mimeTypes.getExcluded();
        return excluded.toArray(new String[excluded.size()]);
    }

	public String[] getExcludedPaths() {
        Set<String> excluded=_paths.getExcluded();
        return excluded.toArray(new String[excluded.size()]);
    }

	public String[] getIncludedAgentPatterns() {
        Set<String> includes=_agentPatterns.getIncluded();
        return includes.toArray(new String[includes.size()]);
    }

	public String[] getIncludedMethods() {
        Set<String> includes=_methods.getIncluded();
        return includes.toArray(new String[includes.size()]);
    }

	public String[] getIncludedMimeTypes() {
        Set<String> includes=_mimeTypes.getIncluded();
        return includes.toArray(new String[includes.size()]);
    }

	public String[] getIncludedPaths() {
        Set<String> includes=_paths.getIncluded();
        return includes.toArray(new String[includes.size()]);
    }
    @Deprecated
	public String[] getMethods() {
        return getIncludedMethods();
    }

	public int getMinGzipSize() {
        return _minGzipSize;
    }

	protected HttpField getVaryField() {
        return _vary;
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.server.handler.HandlerWrapper#handle(java.lang.String, org.eclipse.jetty.server.Request, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        ServletContext context = baseRequest.getServletContext();
        String path = context==null?baseRequest.getRequestURI():URIUtil.addPaths(baseRequest.getServletPath(),baseRequest.getPathInfo());
        LOG.debug("{} handle {} in {}",this,baseRequest,context);

        HttpOutput out = baseRequest.getResponse().getHttpOutput();
        // Are we already being gzipped?
        HttpOutput.Interceptor interceptor = out.getInterceptor();
        while (interceptor!=null)
        {
            if (interceptor instanceof GzipHttpOutputInterceptor)
            {
                LOG.debug("{} already intercepting {}",this,request);
                _handler.handle(target,baseRequest, request, response);
                return;
            }
            interceptor=interceptor.getNextInterceptor();
        }

        // If not a supported method - no Vary because no matter what client, this URI is always excluded
        if (!_methods.matches(baseRequest.getMethod()))
        {
            LOG.debug("{} excluded by method {}",this,request);
            _handler.handle(target,baseRequest, request, response);
            return;
        }

        // If not a supported URI- no Vary because no matter what client, this URI is always excluded
        // Use pathInfo because this is be
        if (!isPathGzipable(path))
        {
            LOG.debug("{} excluded by path {}",this,request);
            _handler.handle(target,baseRequest, request, response);
            return;
        }

        // Exclude non compressible mime-types known from URI extension. - no Vary because no matter what client, this URI is always excluded
        String mimeType = context==null?null:context.getMimeType(path);
        if (mimeType!=null)
        {
            mimeType = MimeTypes.getContentTypeWithoutCharset(mimeType);
            if (!isMimeTypeGzipable(mimeType))
            {
                LOG.debug("{} excluded by path suffix mime type {}",this,request);
                // handle normally without setting vary header
                _handler.handle(target,baseRequest, request, response);
                return;
            }
        }

        if (_checkGzExists && context!=null)
        {
            String realpath=request.getServletContext().getRealPath(path);
            if (realpath!=null)
            {
                File gz=new File(realpath+".gz");
                if (gz.exists())
                {
                    LOG.debug("{} gzip exists {}",this,request);
                    // allow default servlet to handle
                    _handler.handle(target,baseRequest, request, response);
                    return;
                }
            }
        }

        // Special handling for etags
        String etag = baseRequest.getHttpFields().get(HttpHeader.IF_NONE_MATCH);
        if (etag!=null)
        {
            int i=etag.indexOf(GzipHttpContent.ETAG_GZIP_QUOTE);
            if (i>0)
            {
                while (i>=0)
                {
                    etag=etag.substring(0,i)+etag.substring(i+GzipHttpContent.ETAG_GZIP.length());
                    i=etag.indexOf(GzipHttpContent.ETAG_GZIP_QUOTE,i);
                }
                baseRequest.getHttpFields().put(new HttpField(HttpHeader.IF_NONE_MATCH,etag));
            }
        }

        // install interceptor and handle
        out.setInterceptor(new GzipHttpOutputInterceptor(this,getVaryField(),baseRequest.getHttpChannel(),out.getInterceptor(),isSyncFlush()));

        if (_handler!=null)
            _handler.handle(target,baseRequest, request, response);
    }
	protected boolean isAgentGzipable(String ua) {
		if (ua == null) {
            return false;
		}
        return _agentPatterns.matches(ua);
    }

	protected boolean isPathGzipable(String requestURI) {
		if (requestURI == null) {
            return true;
		}
        return _paths.matches(requestURI);
    }


	public void setCheckGzExists(boolean checkGzExists) {
        _checkGzExists = checkGzExists;
    }

	public void setCompressionLevel(int compressionLevel) {
        _compressionLevel = compressionLevel;
    }

	public void setExcludedAgentPatterns(String... patterns) {
        _agentPatterns.getExcluded().clear();
        addExcludedAgentPatterns(patterns);
    }

	public void setExcludedMethods(String... method) {
        _methods.getExcluded().clear();
        _methods.exclude(method);
    }

	public void setExcludedMimeTypes(String... types) {
        _mimeTypes.getExcluded().clear();
        _mimeTypes.exclude(types);
    }

	public void setExcludedPaths(String... pathspecs) {
        _paths.getExcluded().clear();
        _paths.exclude(pathspecs);
    }

	public void setIncludedAgentPatterns(String... patterns) {
        _agentPatterns.getIncluded().clear();
        addIncludedAgentPatterns(patterns);
    }

	public void setIncludedMethods(String... methods) {
        _methods.getIncluded().clear();
        _methods.include(methods);
    }

	public void setIncludedMimeTypes(String... types) {
        _mimeTypes.getIncluded().clear();
        _mimeTypes.include(types);
    }

	public void setIncludedPaths(String... pathspecs) {
        _paths.getIncluded().clear();
        _paths.include(pathspecs);
    }

	public void setMinGzipSize(int minGzipSize) {
        _minGzipSize = minGzipSize;
    }
}
