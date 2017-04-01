package org.eclipse.jetty.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.MultipartConfigElement;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HostPortHttpField;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.server.session.AbstractSession;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.MultiPartInputStreamParser;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class Request implements HttpServletRequest {// 表示HTTP请求
	
    public static final String __MULTIPART_CONFIG_ELEMENT = "org.eclipse.jetty.multipartConfig";
    public static final String __MULTIPART_INPUT_STREAM = "org.eclipse.jetty.multiPartInputStream";
    public static final String __MULTIPART_CONTEXT = "org.eclipse.jetty.multiPartContext";

    private static final Logger LOG = Log.getLogger(Request.class);

    private static final Collection<Locale> __defaultLocale = Collections.singleton(Locale.getDefault());

    private static final int __NONE = 0, _STREAM = 1, __READER = 2;

	private static final MultiMap<String> NO_PARAMS = new MultiMap<>();// 表示没有参数

	public static Request getBaseRequest(ServletRequest request) {//

        if (request instanceof Request) {
            return (Request)request;
		}

		Object channel = request.getAttribute(HttpChannel.class.getName());//

        if (channel instanceof HttpChannel) {
			return ((HttpChannel) channel).getRequest();//
		}

        while (request instanceof ServletRequestWrapper) {
            request = ((ServletRequestWrapper)request).getRequest();
		}

        if (request instanceof Request) {
            return (Request)request;
		}

        return null;
    }

	private final List<ServletRequestAttributeListener> _requestAttributeListeners = new ArrayList<>();//

	private MetaData.Request _metadata;// 表示请求的元数据

    private String _contextPath;
    private String _servletPath;
    private String _pathInfo;

    private boolean _secure;
    private String _asyncNotSupportedSource = null;
    private boolean _newContext;
    private boolean _cookiesExtracted = false;
	private boolean _handled = false;// 表示是否处理完成
	private boolean _paramsExtracted;// 表示请求中的参数是否被提取过
    private boolean _requestedSessionIdFromCookie = false;
	private Attributes _attributes;//
	private Authentication _authentication;//

	private ContextHandler.Context _context;// 请求所在的上下文环境
    private CookieCutter _cookies;
	private DispatcherType _dispatcherType;// 表示请求的派送类型:DispatcherType.REQUEST
    private int _inputState = __NONE;
	private MultiMap<String> _queryParameters;// 表示请求中的查询参数
	private MultiMap<String> _contentParameters;// 表示请求中的内容参数
    private MultiMap<String> _parameters;
	private String _queryEncoding;// 表示请求参数的编码方式
	private String _characterEncoding;// 表示请求域的编码方式
    private BufferedReader _reader;
    private String _readerEncoding;
    private InetSocketAddress _remote;
    private String _requestedSessionId;
    private Map<Object, HttpSession> _savedNewSessions;
    private UserIdentity.Scope _scope;
    private HttpSession _session;
	private SessionManager _sessionManager;//
    private long _timeStamp;
    private MultiPartInputStreamParser _multiPartInputStream; //if the request is a multi-part mime
    private AsyncContextState _async;

	// ***************************************************************************************************
	private final HttpChannel _channel;// HttpChannelOverHttp
	private final HttpInput _input;// HttpInputOverHTTP
	public Request(HttpChannel channel, HttpInput input) {// HttpChannel构造中创建
		_channel = channel;// 该请求关联的通道
		_input = input;// 该请求关联的输出
    }
	// ***************************************************************************************************
    public HttpFields getHttpFields() {
        return _metadata.getFields();
    }
    public HttpInput getHttpInput() {
        return _input;
    }
    public boolean isPush() {
        return Boolean.TRUE.equals(getAttribute("org.eclipse.jetty.pushed"));
    }
    public boolean isPushSupported() {
        return getHttpChannel().getHttpTransport().isPushSupported();
    }
	public PushBuilder getPushBuilder() {

        if (!isPushSupported()) {
            throw new IllegalStateException();
		}

		HttpFields fields = new HttpFields(getHttpFields().size() + 5);

		boolean conditional = false;

		@SuppressWarnings("unused")
		UserIdentity user_identity = null;

		@SuppressWarnings("unused")
		Authentication authentication = null;

        for (HttpField field : getHttpFields()) {
            HttpHeader header = field.getHeader();
            if (header == null) {
                fields.add(field);
            } else {
                switch(header) {
                    case IF_MATCH:
                    case IF_RANGE:
                    case IF_UNMODIFIED_SINCE:
                    case RANGE:
                    case EXPECT:
                    case REFERER:
                    case COOKIE:
                        continue;
                    case AUTHORIZATION:
					user_identity = getUserIdentity();
					authentication = _authentication;
                        continue;
                    case IF_NONE_MATCH:
                    case IF_MODIFIED_SINCE:
					conditional = true;
                        continue;
                    default:
                        fields.add(field);
                }
            }
        }
        String id = null;
        try {
            HttpSession session = getSession();
            if (session != null) {
                session.getLastAccessedTime(); // checks if session is valid
                id = session.getId();
			} else {
                id = getRequestedSessionId();
			}
        } catch(IllegalStateException e) {
            id = getRequestedSessionId();
        }
		PushBuilder builder = new PushBuilderImpl(this, fields, getMethod(), getQueryString(), id, conditional);
        builder.addHeader("referer", getRequestURL().toString());

        // TODO process any set cookies
        // TODO process any user_identity

        return builder;
    }
    public void addEventListener(final EventListener listener) {
        if (listener instanceof ServletRequestAttributeListener) {
            _requestAttributeListeners.add((ServletRequestAttributeListener)listener);
		}
        if (listener instanceof AsyncListener) {
            throw new IllegalArgumentException(listener.getClass().toString());
		}
    }

	// ******************************************************************************
	public void extractParameters() {// 抽取请求中的参数
        if (_paramsExtracted) {
            return;
		}
        _paramsExtracted = true;
        if (_queryParameters == null) {
            extractQueryParameters();
		}
        if (_contentParameters == null) {
            extractContentParameters();
		}
        restoreParameters();
    }

	private void extractQueryParameters() {// 抽取查询参数

		if (_metadata.getURI() == null || !_metadata.getURI().hasQuery()) {// URI为空或者没有查询参数时

			_queryParameters = NO_PARAMS;

		} else {

            _queryParameters = new MultiMap<>();

			if (_queryEncoding == null) {// 表示无编码

                _metadata.getURI().decodeQueryTo(_queryParameters);

			} else {// 表示按指定编码来解析查询参数

                try {

                    _metadata.getURI().decodeQueryTo(_queryParameters, _queryEncoding);

                } catch (UnsupportedEncodingException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.warn(e);
                    } else {
                        LOG.warn(e.toString());
					}
                }
            }
        }
    }

	private void extractContentParameters() {// 抽取内容参数

        String contentType = getContentType();

        if (contentType == null || contentType.isEmpty()) {
			_contentParameters = NO_PARAMS;
        } else {

			_contentParameters = new MultiMap<>();

            contentType = HttpFields.valueParameters(contentType, null);

            int contentLength = getContentLength();

            if (contentLength != 0) {

                if (MimeTypes.Type.FORM_ENCODED.is(contentType) && _inputState == __NONE && _channel.getHttpConfiguration().isFormEncodedMethod(getMethod())) {
                    extractFormParameters(_contentParameters);
                } else if (contentType.startsWith("multipart/form-data") && getAttribute(__MULTIPART_CONFIG_ELEMENT) != null && _multiPartInputStream == null) {
                    extractMultipartParameters(_contentParameters);
                }
            }
        }
    }
	public void extractFormParameters(MultiMap<String> params) {//

        try {
            int maxFormContentSize = -1;
            int maxFormKeys = -1;
            if (_context != null) {
                maxFormContentSize = _context.getContextHandler().getMaxFormContentSize();
                maxFormKeys = _context.getContextHandler().getMaxFormKeys();
            }
            if (maxFormContentSize < 0) {
                Object obj = _channel.getServer().getAttribute("org.eclipse.jetty.server.Request.maxFormContentSize");
                if (obj == null) {
                    maxFormContentSize = 200000;
                } else if (obj instanceof Number) {
                    Number size = (Number)obj;
                    maxFormContentSize = size.intValue();
                } else if (obj instanceof String) {
                    maxFormContentSize = Integer.valueOf((String)obj);
                }
            }

			if (maxFormKeys < 0) {
                Object obj = _channel.getServer().getAttribute("org.eclipse.jetty.server.Request.maxFormKeys");
				if (obj == null) {
					maxFormKeys = 1000;
				} else if (obj instanceof Number) {
                    Number keys = (Number)obj;
                    maxFormKeys = keys.intValue();
				} else if (obj instanceof String) {
                    maxFormKeys = Integer.valueOf((String)obj);
                }
            }

            int contentLength = getContentLength();
			if (contentLength > maxFormContentSize && maxFormContentSize > 0) {
                throw new IllegalStateException("Form too large: " + contentLength + " > " + maxFormContentSize);
            }
            InputStream in = getInputStream();
			if (_input.isAsync()) {
				throw new IllegalStateException("Cannot extract parameters with async IO");
			}

            UrlEncoded.decodeTo(in,params,getCharacterEncoding(),contentLength<0?maxFormContentSize:-1,maxFormKeys);
		} catch (IOException e) {
			if (LOG.isDebugEnabled()) {
				LOG.warn(e);
			} else {
				LOG.warn(e.toString());
			}
        }
    }

	private void restoreParameters() {// 存储参数
		if (_queryParameters == null) {
			extractQueryParameters();
		}
		if (_queryParameters == NO_PARAMS || _queryParameters.size() == 0) {
			_parameters = _contentParameters;
		} else if (_contentParameters == NO_PARAMS || _contentParameters.size() == 0) {
			_parameters = _queryParameters;
		} else {
			_parameters = new MultiMap<>();
			_parameters.addAllValues(_queryParameters);
			_parameters.addAllValues(_contentParameters);
		}
	}
    private void extractMultipartParameters(MultiMap<String> result) {
        try {
            getParts(result);
        } catch (IOException | ServletException e) {
            LOG.warn(e);
            throw new RuntimeException(e);
        }
    }
	// ******************************************************************************

    public HttpChannelState getHttpChannelState() {
        return _channel.getState();
    }
	public Attributes getAttributes() {
		if (_attributes == null) {
			_attributes = new AttributesMap();
		}
        return _attributes;
    }
	public Authentication getAuthentication() {
        return _authentication;
    }

	// ******************************************************************************
	// 下面是实现的接口函数:HttpServletRequest
	@Override
	public String getAuthType() {
        if (_authentication instanceof Authentication.Deferred) {
			setAuthentication(((Authentication.Deferred)_authentication).authenticate(this));
		}            
		if (_authentication instanceof Authentication.User) {
			return ((Authentication.User) _authentication).getAuthMethod();
		}
        return null;
    }
	@Override
	public Cookie[] getCookies() {
		if (_metadata == null || _cookiesExtracted) {
			if (_cookies == null || _cookies.getCookies().length == 0) {
				return null;
			}
			return _cookies.getCookies();
		}
		_cookiesExtracted = true;
		for (String c : _metadata.getFields().getValuesList(HttpHeader.COOKIE)) {
			if (_cookies == null) {
				_cookies = new CookieCutter();
			}
			_cookies.addCookieField(c);
		}
		// *****************************************************************
		// javadoc for Request.getCookies() stipulates null for no cookies
		// *****************************************************************
		if (_cookies == null || _cookies.getCookies().length == 0) {
			return null;
		}
		return _cookies.getCookies();
	}
	@Override
	public long getDateHeader(String name) {
		return _metadata == null ? -1 : _metadata.getFields().getDateField(name);
	}
	@Override
	public String getHeader(String name) {
		return _metadata == null ? null : _metadata.getFields().get(name);
	}
	@Override
	public Enumeration<String> getHeaders(String name) {
		if (_metadata == null) {
			return Collections.emptyEnumeration();
		}
		Enumeration<String> e = _metadata.getFields().getValues(name);
		if (e == null) {
			return Collections.enumeration(Collections.<String> emptyList());
		}
		return e;
	}
	@Override
	public Enumeration<String> getHeaderNames() {
		if (_metadata == null)
			return Collections.emptyEnumeration();
		return _metadata.getFields().getFieldNames();
	}
    @Override
	public int getIntHeader(String name) {
		return _metadata == null ? -1 : (int) _metadata.getFields().getLongField(name);
	}
	@Override
	public String getMethod() {
		return _metadata == null ? null : _metadata.getMethod();
	}
	@Override
	public String getPathInfo() {
		return _pathInfo;
	}
	@Override
	public String getPathTranslated() {
		if (_pathInfo == null || _context == null) {
			return null;
		}
		return _context.getRealPath(_pathInfo);
	}
	@Override
	public String getContextPath() {
		return _contextPath;
	}
	@Override
	public String getQueryString() {
		return _metadata.getURI().getQuery();
	}
	@Override
	public String getRemoteUser() {
		Principal p = getUserPrincipal();
		if (p == null)
			return null;
		return p.getName();
	}
	@Override
	public boolean isUserInRole(String role) {
		if (_authentication instanceof Authentication.Deferred) {
			setAuthentication(((Authentication.Deferred) _authentication).authenticate(this));
		}
		if (_authentication instanceof Authentication.User) {
			return ((Authentication.User) _authentication).isUserInRole(_scope, role);
		}
		return false;
	}
	@Override
	public Principal getUserPrincipal() {
		if (_authentication instanceof Authentication.Deferred) {
			setAuthentication(((Authentication.Deferred) _authentication).authenticate(this));
		}
		if (_authentication instanceof Authentication.User) {
			UserIdentity user = ((Authentication.User) _authentication).getUserIdentity();
			return user.getUserPrincipal();
		}
		return null;
	}
	@Override
	public String getRequestedSessionId() {
		return _requestedSessionId;
	}
	@Override
	public String getRequestURI() {
		MetaData metadata = _metadata;
		return (metadata == null) ? null : _metadata.getURI().getPath();
	}
	@Override
	public StringBuffer getRequestURL() {
		final StringBuffer url = new StringBuffer(128);
		URIUtil.appendSchemeHostPort(url, getScheme(), getServerName(), getServerPort());
		url.append(getRequestURI());
		return url;
	}
	@Override
	public String getServletPath() {
		if (_servletPath == null)
			_servletPath = "";
		return _servletPath;
	}
	@Override
	public HttpSession getSession(boolean create) {
		if (_session != null) {
			if (_sessionManager != null && !_sessionManager.isValid(_session)) {
				_session = null;
			} else {
				return _session;
			}
		}
		if (!create) {
			return null;
		}
		if (getResponse().isCommitted()) {
			throw new IllegalStateException("Response is committed");
		}
		if (_sessionManager == null) {
			throw new IllegalStateException("No SessionManager");
		}
		_session = _sessionManager.newHttpSession(this);
		HttpCookie cookie = _sessionManager.getSessionCookie(_session, getContextPath(), isSecure());
		if (cookie != null) {
			_channel.getResponse().addCookie(cookie);
		}
		return _session;
	}
	@Override
	public HttpSession getSession() {
		return getSession(true);
	}
	@Override
	public String changeSessionId() {
		HttpSession session = getSession(false);
		if (session == null) {
			throw new IllegalStateException("No session");
		}
		if (session instanceof AbstractSession) {
			AbstractSession abstractSession = ((AbstractSession) session);
			abstractSession.renewId(this);
			if (getRemoteUser() != null) {
				abstractSession.setAttribute(AbstractSession.SESSION_CREATED_SECURE, Boolean.TRUE);
			}
			if (abstractSession.isIdChanged()) {
				_channel.getResponse().addCookie(_sessionManager.getSessionCookie(abstractSession, getContextPath(), isSecure()));
			}
		}
		return session.getId();
	}
	@Override
	public boolean isRequestedSessionIdValid() {
		if (_requestedSessionId == null) {
			return false;
		}
		HttpSession session = getSession(false);
		return (session != null && _sessionManager.getSessionIdManager().getClusterId(_requestedSessionId)
				.equals(_sessionManager.getClusterId(session)));
	}
	@Override
	public boolean isRequestedSessionIdFromCookie() {
		return _requestedSessionId != null && _requestedSessionIdFromCookie;
	}
	@Override
	public boolean isRequestedSessionIdFromURL() {
		return _requestedSessionId != null && !_requestedSessionIdFromCookie;
	}
	@Override
	public boolean isRequestedSessionIdFromUrl() {
		return _requestedSessionId != null && !_requestedSessionIdFromCookie;
	}
	@Override
	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
		if (_authentication instanceof Authentication.Deferred) {
			setAuthentication(((Authentication.Deferred) _authentication).authenticate(this, response));
			return !(_authentication instanceof Authentication.ResponseSent);
		}
		response.sendError(HttpStatus.UNAUTHORIZED_401);
		return false;
	}
	@Override
	public void login(String username, String password) throws ServletException {
		if (_authentication instanceof Authentication.Deferred) {
			_authentication = ((Authentication.Deferred) _authentication).login(username, password, this);
			if (_authentication == null) {
				throw new Authentication.Failed("Authentication failed for username '" + username + "'");
			}
		} else {
			throw new Authentication.Failed("Authenticated failed for username '" + username
					+ "'. Already authenticated as " + _authentication);
		}
	}
	@Override
	public void logout() throws ServletException {
		if (_authentication instanceof Authentication.User) {
			((Authentication.User) _authentication).logout();
		}
		_authentication = Authentication.UNAUTHENTICATED;
	}
	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		if (getContentType() == null || !getContentType().startsWith("multipart/form-data")) {
			throw new ServletException("Content-Type != multipart/form-data");
		}
		return getParts(null);
	}
	@Override
	public Part getPart(String name) throws IOException, ServletException {
		getParts();
		return _multiPartInputStream.getPart(name);
	}
	@Override
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
		throw new ServletException("HttpServletRequest.upgrade() not supported in Jetty");
	}
	// ******************************************************************************
	// 下面是实现的接口函数:ServletRequest
	@Override
	public Object getAttribute(String name) {
		if (name.startsWith("org.eclipse.jetty")) {
			if (Server.class.getName().equals(name)) {
				return _channel.getServer();
			}
			if (HttpChannel.class.getName().equals(name)) {
				return _channel;
			}
			if (HttpConnection.class.getName().equals(name) && _channel.getHttpTransport() instanceof HttpConnection) {// ????
				return _channel.getHttpTransport();
			}
		}
		return (_attributes == null) ? null : _attributes.getAttribute(name);
	}
	@Override
	public Enumeration<String> getAttributeNames() {
		if (_attributes == null) {
			return Collections.enumeration(Collections.<String> emptyList());
		}
		return AttributesMap.getAttributeNamesCopy(_attributes);
	}
	@Override
	public void setAttribute(String name, Object value) {
		Object old_value = _attributes == null ? null : _attributes.getAttribute(name);
		if ("org.eclipse.jetty.server.Request.queryEncoding".equals(name)) {
			setQueryEncoding(value == null ? null : value.toString());
		} else if ("org.eclipse.jetty.server.sendContent".equals(name)) {
			LOG.warn("Deprecated: org.eclipse.jetty.server.sendContent");
		}
		if (_attributes == null) {
			_attributes = new AttributesMap();
		}
		_attributes.setAttribute(name, value);
		if (!_requestAttributeListeners.isEmpty()) {
			final ServletRequestAttributeEvent event = new ServletRequestAttributeEvent(_context, this, name,
					old_value == null ? value : old_value);
			for (ServletRequestAttributeListener l : _requestAttributeListeners) {
				if (old_value == null) {
					l.attributeAdded(event);
				} else if (value == null) {
					l.attributeRemoved(event);
				} else {
					l.attributeReplaced(event);
				}
			}
		}
	}

	@Override
	public void removeAttribute(String name) {
		Object old_value = _attributes == null ? null : _attributes.getAttribute(name);
		if (_attributes != null) {
			_attributes.removeAttribute(name);
		}
		if (old_value != null && !_requestAttributeListeners.isEmpty()) {
			final ServletRequestAttributeEvent event = new ServletRequestAttributeEvent(_context, this, name,
					old_value);
			for (ServletRequestAttributeListener listener : _requestAttributeListeners) {
				listener.attributeRemoved(event);
			}
		}
	}

	@Override
	public String getCharacterEncoding() {
		if (_characterEncoding == null) {
			getContentType();
		}
		return _characterEncoding;
	}
	@Override
	public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
		if (_inputState != __NONE) {
			return;
		}
		_characterEncoding = encoding;
		// check encoding is supported
		if (!StringUtil.isUTF8(encoding)) {
			try {
				Charset.forName(encoding);
			} catch (UnsupportedCharsetException e) {
				throw new UnsupportedEncodingException(e.getMessage());
			}
		}
	}
	@Override
	public int getContentLength() {
		if (_metadata.getContentLength() != Long.MIN_VALUE) {
			return (int) _metadata.getContentLength();
		}
		return (int) _metadata.getFields().getLongField(HttpHeader.CONTENT_LENGTH.toString());
	}
	@Override
	public long getContentLengthLong() {
		if (_metadata.getContentLength() != Long.MIN_VALUE) {
			return _metadata.getContentLength();
		}
		return _metadata.getFields().getLongField(HttpHeader.CONTENT_LENGTH.toString());
	}
	@Override
	public String getContentType() {// 获取HTTP请求中的内容域(同时得到字符编码)

		String content_type = _metadata.getFields().get(HttpHeader.CONTENT_TYPE);

		if (_characterEncoding == null && content_type != null) {//

			MimeTypes.Type mime = MimeTypes.CACHE.get(content_type);

			String charset = (mime == null || mime.getCharset() == null) ? MimeTypes.getCharsetFromContentType(content_type) : mime.getCharset().toString();

			if (charset != null) {
				_characterEncoding = charset;
			}
		}

		return content_type;
	}
	@Override
	public ServletInputStream getInputStream() throws IOException {
		if (_inputState != __NONE && _inputState != _STREAM) {
			throw new IllegalStateException("READER");
		}
		_inputState = _STREAM;
		if (_channel.isExpecting100Continue()) {
			_channel.continue100(_input.available());
		}
		return _input;
	}
	@Override
	public String getParameter(String name) {
		if (!_paramsExtracted) {
			extractParameters();
		}
		if (_parameters == null) {
			restoreParameters();
		}
		return _parameters.getValue(name, 0);
	}
	@Override
	public Enumeration<String> getParameterNames() {
		if (!_paramsExtracted) {
			extractParameters();
		}
		if (_parameters == null) {
			restoreParameters();
		}
		return Collections.enumeration(_parameters.keySet());
	}
	@Override
	public String[] getParameterValues(String name) {
		if (!_paramsExtracted) {
			extractParameters();
		}
		if (_parameters == null) {
			restoreParameters();
		}
		List<String> vals = _parameters.getValues(name);
		if (vals == null) {
			return null;
		}
		return vals.toArray(new String[vals.size()]);
	}
	@Override
	public Map<String, String[]> getParameterMap() {
		if (!_paramsExtracted) {
			extractParameters();
		}
		if (_parameters == null) {
			restoreParameters();
		}
		return Collections.unmodifiableMap(_parameters.toStringArrayMap());
	}
	@Override
	public BufferedReader getReader() throws IOException {
		if (_inputState != __NONE && _inputState != __READER) {
			throw new IllegalStateException("STREAMED");
		}
		if (_inputState == __READER) {
			return _reader;
		}
		String encoding = getCharacterEncoding();
		if (encoding == null) {
			encoding = StringUtil.__ISO_8859_1;
		}
		if (_reader == null || !encoding.equalsIgnoreCase(_readerEncoding)) {
			final ServletInputStream in = getInputStream();
			_readerEncoding = encoding;
			_reader = new BufferedReader(new InputStreamReader(in, encoding)) {
				@Override
				public void close() throws IOException {
					in.close();
				}
			};
		}
		_inputState = __READER;
		return _reader;
	}
	@Override
	public String getRemoteAddr() {
		InetSocketAddress remote = _remote;
		if (remote == null) {
			remote = _channel.getRemoteAddress();
		}
		if (remote == null) {
			return "";
		}
		InetAddress address = remote.getAddress();
		if (address == null) {
			return remote.getHostString();
		}
		return address.getHostAddress();
	}
	@Override
	public String getRemoteHost() {
		InetSocketAddress remote = _remote;
		if (remote == null) {
			remote = _channel.getRemoteAddress();
		}
		return remote == null ? "" : remote.getHostString();
	}
	@Override
	public Locale getLocale() {
		if (_metadata == null) {
			return Locale.getDefault();
		}
		List<String> acceptable = _metadata.getFields().getQualityCSV(HttpHeader.ACCEPT_LANGUAGE);
		if (acceptable.isEmpty()) {// handle no locale
			return Locale.getDefault();
		}
		String language = acceptable.get(0);
		language = HttpFields.stripParameters(language);
		String country = "";
		int dash = language.indexOf('-');
		if (dash > -1) {
			country = language.substring(dash + 1).trim();
			language = language.substring(0, dash).trim();
		}
		return new Locale(language, country);
	}
	@Override
	public Enumeration<Locale> getLocales() {
		if (_metadata == null) {
			return Collections.enumeration(__defaultLocale);
		}
		List<String> acceptable = _metadata.getFields().getQualityCSV(HttpHeader.ACCEPT_LANGUAGE);
		if (acceptable.isEmpty()) {// handle no locale
			return Collections.enumeration(__defaultLocale);
		}
		List<Locale> locales = acceptable.stream().map(language -> {
			language = HttpFields.stripParameters(language);
			String country = "";
			int dash = language.indexOf('-');
			if (dash > -1) {
				country = language.substring(dash + 1).trim();
				language = language.substring(0, dash).trim();
			}
			return new Locale(language, country);
		}).collect(Collectors.toList());
		return Collections.enumeration(locales);
	}
	@Override
	public boolean isSecure() {
		return _secure;
	}
	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		if (path == null || _context == null) {
			return null;
		}
		if (!path.startsWith("/")) {// handle relative path
			String relTo = URIUtil.addPaths(_servletPath, _pathInfo);
			int slash = relTo.lastIndexOf("/");
			if (slash > 1) {
				relTo = relTo.substring(0, slash + 1);
			} else {
				relTo = "/";
			}
			path = URIUtil.addPaths(relTo, path);
		}
		return _context.getRequestDispatcher(path);
	}
	@Override
	public String getRealPath(String path) {
		if (_context == null) {
			return null;
		}
		return _context.getRealPath(path);
	}
	@Override
	public int getRemotePort() {
		InetSocketAddress remote = _remote;
		if (remote == null) {
			remote = _channel.getRemoteAddress();
		}
		return remote == null ? 0 : remote.getPort();
	}
    @Override
	public String getLocalName() {
		if (_channel == null) {
			try {
				String name = InetAddress.getLocalHost().getHostName();
				if (StringUtil.ALL_INTERFACES.equals(name)) {
					return null;
				}
				return name;
			} catch (java.net.UnknownHostException e) {
				LOG.ignore(e);
			}
        }
		InetSocketAddress local = _channel.getLocalAddress();
		return local.getHostString();
    }
    @Override
	public String getLocalAddr() {
		if (_channel == null) {
			try {
                String name =InetAddress.getLocalHost().getHostAddress();
				if (StringUtil.ALL_INTERFACES.equals(name)) {
					return null;
				}
                return name;
			} catch (java.net.UnknownHostException e) {
                LOG.ignore(e);
            }
        }
        InetSocketAddress local=_channel.getLocalAddress();
		if (local == null) {
			return "";
		}
        InetAddress address = local.getAddress();
		if (address == null) {
			return local.getHostString();
		}
        return address.getHostAddress();
    }
    @Override
	public int getLocalPort() {
		if (_channel == null) {
			return 0;
		}
		InetSocketAddress local = _channel.getLocalAddress();
        return local.getPort();
    }
	@Override
	public String getProtocol() {
		if (_metadata == null) {
			return null;
		}
		HttpVersion version = _metadata.getVersion();
		if (version == null) {
			return null;
		}
		return version.toString();
	}
	@Override
	public String getScheme() {
		String scheme = _metadata == null ? null : _metadata.getURI().getScheme();
		return scheme == null ? HttpScheme.HTTP.asString() : scheme;
	}
	@Override
	public String getServerName() {
		String name = _metadata.getURI().getHost();
		if (name != null) {// return already determined host
			return name;
		}
		return findServerName();
	}
	@Override
	public int getServerPort() {
		HttpURI uri = _metadata.getURI();
		int port = (uri.getHost() == null) ? findServerPort() : uri.getPort();
		if (port <= 0) {// if no port specified, return the default port for the
						// scheme
			if (getScheme().equalsIgnoreCase(URIUtil.HTTPS)) {
				return 443;
			}
			return 80;
		}
		return port;// return a specific port
	}
	@Override
	public ServletContext getServletContext() {
		return _context;
	}
	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		if (_asyncNotSupportedSource != null) {
			throw new IllegalStateException("!asyncSupported: " + _asyncNotSupportedSource);
		}
		HttpChannelState state = getHttpChannelState();
		if (_async == null) {
			_async = new AsyncContextState(state);
		}
		AsyncContextEvent event = new AsyncContextEvent(_context, _async, state, this, this, getResponse());
		state.startAsync(event);
		return _async;
	}
	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
			throws IllegalStateException {
		if (_asyncNotSupportedSource != null) {
			throw new IllegalStateException("!asyncSupported: " + _asyncNotSupportedSource);
		}
		HttpChannelState state = getHttpChannelState();
		if (_async == null) {
			_async = new AsyncContextState(state);
		}
		AsyncContextEvent event = new AsyncContextEvent(_context, _async, state, this, servletRequest, servletResponse);
		event.setDispatchContext(getServletContext());
		event.setDispatchPath(URIUtil.addPaths(getServletPath(), getPathInfo()));
		state.startAsync(event);
		return _async;
	}
	@Override
	public boolean isAsyncStarted() {
		return getHttpChannelState().isAsyncStarted();
	}
	@Override
	public boolean isAsyncSupported() {
		return _asyncNotSupportedSource == null;
	}
	@Override
	public AsyncContext getAsyncContext() {
		HttpChannelState state = getHttpChannelState();
		if (_async == null || !state.isAsyncStarted()) {
			throw new IllegalStateException(state.getStatusString());
		}
		return _async;
	}
	@Override
	public DispatcherType getDispatcherType() {
		return _dispatcherType;
	}
	// ******************************************************************************
	public void setCharacterEncodingUnchecked(String encoding) {
		_characterEncoding = encoding;
	}
	public void setContentType(String contentType) {
		_metadata.getFields().put(HttpHeader.CONTENT_TYPE, contentType);
	}
	public HttpChannel getHttpChannel() {
		return _channel;
	}
	public long getContentRead() {
		return _input.getContentConsumed();
	}
	public Context getContext() {
		return _context;
	}
	public int getInputState() {
		return _inputState;
	}


    public MultiMap<String> getQueryParameters() {
        return _queryParameters;
    }
    public void setQueryParameters(MultiMap<String> queryParameters) {
        _queryParameters = queryParameters;
    }
    public void setContentParameters(MultiMap<String> contentParameters) {
        _contentParameters = contentParameters;
    }
    public void resetParameters() {
        _parameters = null;
    }
    public HttpVersion getHttpVersion() {
        return _metadata==null?null:_metadata.getVersion();
    }
    public String getQueryEncoding() {
        return _queryEncoding;
    }
    public InetSocketAddress getRemoteInetSocketAddress() {
        InetSocketAddress remote = _remote;
		if (remote == null) {
			remote = _channel.getRemoteAddress();
		}
        return remote;
    }
    public Response getResponse() {
        return _channel.getResponse();
    }

	public StringBuilder getRootURL() {// 鑾峰彇璇ヨ姹傜殑鏍硅矾寰�--鍖呮嫭鍗忚銆佹湇鍔″悕鍜岀鍙ｅ彿
        StringBuilder url = new StringBuilder(128);
		URIUtil.appendSchemeHostPort(url, getScheme(), getServerName(), getServerPort());
        return url;
    }

	private String findServerName() {
		HttpField host = _metadata.getFields().getField(HttpHeader.HOST);
		if (host != null) {
            // TODO is this needed now?
			HostPortHttpField authority = (host instanceof HostPortHttpField) ? ((HostPortHttpField) host)
					: new HostPortHttpField(host.getValue());
			_metadata.getURI().setAuthority(authority.getHost(), authority.getPort());
            return authority.getHost();
        }

		String name = getLocalName();// return host from connection
		if (name != null) {
			return name;
		}

		try {// return the local host
            return InetAddress.getLocalHost().getHostAddress();
		} catch (java.net.UnknownHostException e) {
            LOG.ignore(e);
        }
        return null;
    }

	private int findServerPort() {
        // Return host from header field
        HttpField host = _metadata.getFields().getField(HttpHeader.HOST);
		if (host != null) {
            // TODO is this needed now?
			HostPortHttpField authority = (host instanceof HostPortHttpField) ? ((HostPortHttpField) host)
					: new HostPortHttpField(host.getValue());
            _metadata.getURI().setAuthority(authority.getHost(),authority.getPort());
            return authority.getPort();
        }

        // Return host from connection
		if (_channel != null) {
			return getLocalPort();
		}
        return -1;
    }

    public String getServletName() {
        if (_scope != null)
            return _scope.getName();
        return null;
    }
    public ServletResponse getServletResponse() {
        return _channel.getResponse();
    }
    public SessionManager getSessionManager() {
        return _sessionManager;
    }
    public long getTimeStamp() {
        return _timeStamp;
    }
    public HttpURI getHttpURI() {
        return _metadata == null ? null : _metadata.getURI();
    }
    public void setHttpURI(HttpURI uri) {
        _metadata.setURI(uri);
    }
    public UserIdentity getUserIdentity() {
        if (_authentication instanceof Authentication.Deferred) {
            setAuthentication(((Authentication.Deferred)_authentication).authenticate(this));
		}
        if (_authentication instanceof Authentication.User) {
            return ((Authentication.User)_authentication).getUserIdentity();
		}
        return null;
    }
    public UserIdentity getResolvedUserIdentity() {
        if (_authentication instanceof Authentication.User) {
            return ((Authentication.User)_authentication).getUserIdentity();
		}
        return null;
    }
    public UserIdentity.Scope getUserIdentityScope() {
        return _scope;
    }

    public boolean isHandled() {
        return _handled;
    }


    public void setSecure(boolean secure) {
        _secure = secure;
    }

    public HttpSession recoverNewSession(Object key) {
        if (_savedNewSessions == null) {
            return null;
		}
        return _savedNewSessions.get(key);
    }
    public void setMetaData(org.eclipse.jetty.http.MetaData.Request request) {
        _metadata = request;
        setMethod(request.getMethod());
        HttpURI uri = request.getURI();

        String path = uri.getDecodedPath();
        String info;
        if (path == null || path.length() == 0) {
            if (uri.isAbsolute()) {
                path="/";
                uri.setPath(path);
            } else {
                setPathInfo("");
                throw new BadMessageException(400,"Bad URI");
            }
            info=path;
        } else if (!path.startsWith("/")) {
            if (!"*".equals(path) && !HttpMethod.CONNECT.is(getMethod())) {
                setPathInfo(path);
                throw new BadMessageException(400,"Bad URI");
            }
            info=path;
        }
        else
            info = URIUtil.canonicalPath(path);// TODO should this be done prior to decoding???

        if (info == null) {
            setPathInfo(path);
            throw new BadMessageException(400,"Bad URI");
        }
        setPathInfo(info);
    }
    public org.eclipse.jetty.http.MetaData.Request getMetaData() {
        return _metadata;
    }
    public boolean hasMetaData() {
		return _metadata != null;
    }
    protected void recycle() {
        _metadata = null;
        if (_context != null) {
            throw new IllegalStateException("Request in context!");
		}
        if (_inputState == __READER) {
            try {
                int r = _reader.read();
                while (r != -1) {
                    r = _reader.read();
				}
            } catch (Exception e) {
                LOG.ignore(e);
                _reader = null;
            }
        }

		_dispatcherType = null;
        setAuthentication(Authentication.NOT_CHECKED);
        getHttpChannelState().recycle();
		if (_async != null) {
			_async.reset();
		}
        _async=null;
        _asyncNotSupportedSource = null;
        _handled = false;
		if (_attributes != null) {
			_attributes.clearAttributes();
		}
        _characterEncoding = null;
        _contextPath = null;
		if (_cookies != null) {
			_cookies.reset();
		}
        _cookiesExtracted = false;
        _context = null;
        _newContext=false;
        _pathInfo = null;
        _queryEncoding = null;
        _requestedSessionId = null;
        _requestedSessionIdFromCookie = false;
        _secure=false;
        _session = null;
        _sessionManager = null;
        _scope = null;
        _servletPath = null;
        _timeStamp = 0;
        _queryParameters = null;
        _contentParameters = null;
        _parameters = null;
        _paramsExtracted = false;
        _inputState = __NONE;

		if (_savedNewSessions != null) {
			_savedNewSessions.clear();
		}
        _savedNewSessions=null;
        _multiPartInputStream = null;
        _remote=null;
        _input.recycle();
    }

	public void removeEventListener(final EventListener listener) {
        _requestAttributeListeners.remove(listener);
    }

	public void saveNewSession(Object key, HttpSession session) {
		if (_savedNewSessions == null) {
			_savedNewSessions = new HashMap<>();
		}
        _savedNewSessions.put(key,session);
    }

	public void setAsyncSupported(boolean supported, String source) {
        _asyncNotSupportedSource = supported?null:(source==null?"unknown":source);
    }

	public void setAttributes(Attributes attributes) {
        _attributes = attributes;
    }

	public void setAuthentication(Authentication authentication) {
        _authentication = authentication;
    }

	public void setContext(Context context) {
        _newContext = _context != context;
        _context = context;
    }

	public boolean takeNewContext() {
        boolean nc = _newContext;
        _newContext = false;
        return nc;
    }

	public void setContextPath(String contextPath) {
        _contextPath = contextPath;
    }
	public void setCookies(Cookie[] cookies) {
		if (_cookies == null) {
			_cookies = new CookieCutter();
		}
        _cookies.setCookies(cookies);
    }
	public void setDispatcherType(DispatcherType type) {
        _dispatcherType = type;
    }
	public void setHandled(boolean h) {
        _handled = h;
    }
	public void setMethod(String method) {
        _metadata.setMethod(method);
    }
	public boolean isHead() {
        return _metadata!=null && HttpMethod.HEAD.is(_metadata.getMethod());
    }

	public void setPathInfo(String pathInfo) {
        _pathInfo = pathInfo;
    }

	public void setQueryEncoding(String queryEncoding) {
        _queryEncoding = queryEncoding;
    }
	public void setQueryString(String queryString) {
        _metadata.getURI().setQuery(queryString);
        _queryEncoding = null; //assume utf-8
    }
	public void setRemoteAddr(InetSocketAddress addr) {
        _remote = addr;
    }
	public void setRequestedSessionId(String requestedSessionId) {
		_requestedSessionId = requestedSessionId;
    }
	public void setRequestedSessionIdFromCookie(boolean requestedSessionIdCookie) {
        _requestedSessionIdFromCookie = requestedSessionIdCookie;
    }
	public void setURIPathQuery(String requestURI) {
        _metadata.getURI().setPathQuery(requestURI);
    }
	public void setScheme(String scheme) {
        _metadata.getURI().setScheme(scheme);
    }
	public void setAuthority(String host, int port) {
		_metadata.getURI().setAuthority(host, port);
    }
	public void setServletPath(String servletPath) {
        _servletPath = servletPath;
    }

	public void setSession(HttpSession session) {
        _session = session;
    }

	public void setSessionManager(SessionManager sessionManager) {
        _sessionManager = sessionManager;
    }
    public void setTimeStamp(long ts) {
        _timeStamp = ts;
    }
    public void setUserIdentityScope(UserIdentity.Scope scope) {
        _scope = scope;
    }

    @Override
    public String toString() {
		return String.format("%s%s%s %s%s@%x", getClass().getSimpleName(), _handled ? "[" : "(", getMethod(),
				getHttpURI(), _handled ? "]" : ")", hashCode());
    }


    private Collection<Part> getParts(MultiMap<String> params) throws IOException, ServletException {
        if (_multiPartInputStream == null) {
            _multiPartInputStream = (MultiPartInputStreamParser)getAttribute(__MULTIPART_INPUT_STREAM);
		}
        if (_multiPartInputStream == null) {
            MultipartConfigElement config = (MultipartConfigElement)getAttribute(__MULTIPART_CONFIG_ELEMENT);
            if (config == null) {
                throw new IllegalStateException("No multipart config for servlet");
			}
            _multiPartInputStream = new MultiPartInputStreamParser(getInputStream(),
                                                             getContentType(), config,
                                                             (_context != null?(File)_context.getAttribute("javax.servlet.context.tempdir"):null));

            setAttribute(__MULTIPART_INPUT_STREAM, _multiPartInputStream);
            setAttribute(__MULTIPART_CONTEXT, _context);
            Collection<Part> parts = _multiPartInputStream.getParts(); //causes parsing
            ByteArrayOutputStream os = null;
            for (Part p : parts) {
                MultiPartInputStreamParser.MultiPart mp = (MultiPartInputStreamParser.MultiPart)p;
                if (mp.getContentDispositionFilename() == null)
                {
                    // Servlet Spec 3.0 pg 23, parts without filename must be put into params.
                    String charset = null;
                    if (mp.getContentType() != null)
                        charset = MimeTypes.getCharsetFromContentType(mp.getContentType());

                    try (InputStream is = mp.getInputStream())
                    {
                        if (os == null)
                            os = new ByteArrayOutputStream();
                        IO.copy(is, os);
                        String content=new String(os.toByteArray(),charset==null?StandardCharsets.UTF_8:Charset.forName(charset));
                        if (_contentParameters == null)
                            _contentParameters = params == null ? new MultiMap<>() : params;
                        _contentParameters.add(mp.getName(), content);
                    }
                    os.reset();
                }
            }
        }
        return _multiPartInputStream.getParts();
    }

    public void mergeQueryParameters(String oldQuery,String newQuery, boolean updateQueryString) {
        // TODO  This is seriously ugly

        MultiMap<String> newQueryParams = null;
        // Have to assume ENCODING because we can't know otherwise.
        if (newQuery != null) {
            newQueryParams = new MultiMap<>();
            UrlEncoded.decodeTo(newQuery, newQueryParams, UrlEncoded.ENCODING);
        }

        MultiMap<String> oldQueryParams = _queryParameters;
        if (oldQueryParams == null && oldQuery != null) {
            oldQueryParams = new MultiMap<>();
            UrlEncoded.decodeTo(oldQuery, oldQueryParams, getQueryEncoding());
        }

        MultiMap<String> mergedQueryParams;
        if (newQueryParams==null || newQueryParams.size()==0)
            mergedQueryParams=oldQueryParams==null?NO_PARAMS:oldQueryParams;
        else if (oldQueryParams==null || oldQueryParams.size()==0)
            mergedQueryParams=newQueryParams==null?NO_PARAMS:newQueryParams;
        else {
            // Parameters values are accumulated.
            mergedQueryParams=new MultiMap<>(newQueryParams);
            mergedQueryParams.addAllValues(oldQueryParams);
        }

        setQueryParameters(mergedQueryParams);
        resetParameters();

		if (updateQueryString) {
			if (newQuery == null) {
				setQueryString(oldQuery);
			} else if (oldQuery == null) {
				setQueryString(newQuery);
			} else {
                // Build the new merged query string, parameters in the
                // new query string hide parameters in the old query string.
                StringBuilder mergedQuery = new StringBuilder();
				if (newQuery != null) {
					mergedQuery.append(newQuery);
				}
				for (Map.Entry<String, List<String>> entry : mergedQueryParams.entrySet()) {
					if (newQueryParams != null && newQueryParams.containsKey(entry.getKey())) {
						continue;
					}
					for (String value : entry.getValue()) {
						if (mergedQuery.length() > 0) {
							mergedQuery.append("&");
						}
                        URIUtil.encodePath(mergedQuery,entry.getKey());
                        mergedQuery.append('=');
                        URIUtil.encodePath(mergedQuery,value);
                    }
                }
                setQueryString(mergedQuery.toString());
            }
        }
    }
}
