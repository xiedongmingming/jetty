package org.eclipse.jetty.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.IllegalSelectorException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.http.DateGenerator;
import org.eclipse.jetty.http.HttpContent;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpGenerator;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.eclipse.jetty.util.QuotedStringTokenizer;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class Response implements HttpServletResponse {

	private static final Logger LOG = Log.getLogger(Response.class);

    private static final String __COOKIE_DELIM = "\",;\\ \t";
    private final static String __01Jan1970_COOKIE = DateGenerator.formatCookieDate(0).trim();
	public final static String SET_INCLUDE_HEADER_PREFIX = "org.eclipse.jetty.server.include.";
	public final static String HTTP_ONLY_COMMENT = "__HTTP_ONLY__";
    private final static int __MIN_BUFFER_SIZE = 1;
    private final static HttpField __EXPIRES_01JAN1970 = new PreEncodedHttpField(HttpHeader.EXPIRES,DateGenerator.__01Jan1970);
    
    //cookie building buffer. Reduce garbage for cookie using applications
    private static final ThreadLocal<StringBuilder> __cookieBuilder = new ThreadLocal<StringBuilder>() {
       @Override
       protected StringBuilder initialValue() {
          return new StringBuilder(128);
       }
    };
	// ****************************************************************
	private OutputType _outputType = OutputType.NONE;// 表示输出类型

	public enum OutputType {// 第一种表示初始状态(后面两种则只能使用其中一种(且不能改变))
        NONE,
        STREAM,
        WRITER
    }

	private final HttpOutput _out;// 用于响应输出--流式输出(字节流)
	private ResponseWriter _writer;// 用于输出--字节时
	// ****************************************************************
	private final HttpChannel _channel;// 表示该HTTP连接所在的通道
	private final HttpFields _fields = new HttpFields();// 用于存放响应的各个域
    private final AtomicInteger _include = new AtomicInteger();
	private String _reason;// 用于描述错误原因
	private Locale _locale;//

    private boolean _explicitEncoding;

	private int _status = HttpStatus.OK_200;// 表示响应的状态
    private String _contentType;
	private String _characterEncoding;// 初始为请求中的CONTENT-TYPE域中的值
	private long _contentLength = -1;// ????
	private MimeTypes.Type _mimeType;// ????
	// ****************************************************************
	// 构造函数
    public Response(HttpChannel channel, HttpOutput out) {
        _channel = channel;
        _out = out;
    }
	// ****************************************************************

    public HttpChannel getHttpChannel() {
        return _channel;
    }
    protected void recycle() {
        _status = HttpStatus.OK_200;
        _reason = null;
        _locale = null;
        _mimeType = null;
        _characterEncoding = null;
        _contentType = null;
        _outputType = OutputType.NONE;
        _contentLength = -1;
        _out.recycle();
        _fields.clear();
        _explicitEncoding=false;
    }
    public HttpOutput getHttpOutput() {
        return _out;
    }
    public boolean isIncluding() {
        return _include.get() > 0;
    }
    public void include() {
        _include.incrementAndGet();
    }
    public void included() {
        _include.decrementAndGet();
        if (_outputType == OutputType.WRITER) {
            _writer.reopen();
        }
        _out.reopen();
    }
    public void addCookie(HttpCookie cookie) {
        addSetCookie(cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath(), cookie.getMaxAge(), cookie.getComment(), cookie.isSecure(), cookie.isHttpOnly(), cookie.getVersion());;
    }

	// ****************************************************************************************
	// 下面是实现的接口函数:HttpServletResponse
    @Override
    public void addCookie(Cookie cookie) {
    	
        String comment = cookie.getComment();
        
        boolean httpOnly = false;
        
        if (comment != null) {
        	
            int i = comment.indexOf(HTTP_ONLY_COMMENT);
            
            if (i >= 0) {
            	
                httpOnly = true;
                
                comment = comment.replace(HTTP_ONLY_COMMENT, "").trim();
                
				if (comment.length() == 0) {
					comment = null;
				}
            }
        }
        addSetCookie(cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath(), cookie.getMaxAge(), comment, cookie.getSecure(), httpOnly || cookie.isHttpOnly(), cookie.getVersion());
    }
    @Override
    public boolean containsHeader(String name) {
        return _fields.containsKey(name);
    }
    @Override
    public String encodeURL(String url) {
    	
        final Request request = _channel.getRequest();
        
        SessionManager sessionManager = request.getSessionManager();
        
		if (sessionManager == null) {
			return url;
		}

        HttpURI uri = null;
        
		if (sessionManager.isCheckingRemoteSessionIdEncoding() && URIUtil.hasScheme(url)) {
			
            uri = new HttpURI(url);

            String path = uri.getPath();
            
            path = (path == null ? "" : path);
            
            int port = uri.getPort();
            
			if (port < 0) {
				port = HttpScheme.HTTPS.asString().equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
			}

			if (!request.getServerName().equalsIgnoreCase(uri.getHost())) {
				return url;
			}
			
			if (request.getServerPort() != port) {
				return url;
			}
			
			if (!path.startsWith(request.getContextPath())) {
                return url;
			}
        }

        String sessionURLPrefix = sessionManager.getSessionIdPathParameterNamePrefix();
        
		if (sessionURLPrefix == null) {
			return url;
		}

		if (url == null) {
			return null;
		}

		if ((sessionManager.isUsingCookies() && request.isRequestedSessionIdFromCookie()) || !sessionManager.isUsingURLs()) {
			
            int prefix = url.indexOf(sessionURLPrefix);
            
			if (prefix != -1) {
				
                int suffix = url.indexOf("?", prefix);
                
				if (suffix < 0) {
					suffix = url.indexOf("#", prefix);
				}

				if (suffix <= prefix) {
					return url.substring(0, prefix);
				}
				
                return url.substring(0, prefix) + url.substring(suffix);
            }
            return url;
        }

        HttpSession session = request.getSession(false);


		if (session == null) {
			return url;
		}


		if (!sessionManager.isValid(session)) {
			return url;
		}

        String id = sessionManager.getNodeId(session);

		if (uri == null) {
			uri = new HttpURI(url);
		}

        int prefix = url.indexOf(sessionURLPrefix);
        
		if (prefix != -1) {
			
            int suffix = url.indexOf("?", prefix);
            
			if (suffix < 0) {
				suffix = url.indexOf("#", prefix);
			}

			if (suffix <= prefix) {
				return url.substring(0, prefix + sessionURLPrefix.length()) + id;
			}
			
			return url.substring(0, prefix + sessionURLPrefix.length()) + id + url.substring(suffix);
        }

        int suffix = url.indexOf('?');
		if (suffix < 0) {
			suffix = url.indexOf('#');
		}
		if (suffix < 0) {
			return url + ((HttpScheme.HTTPS.is(uri.getScheme()) || HttpScheme.HTTP.is(uri.getScheme())) && uri.getPath() == null ? "/" : "") + sessionURLPrefix + id;
        }

        return url.substring(0, suffix) +
                ((HttpScheme.HTTPS.is(uri.getScheme()) || HttpScheme.HTTP.is(uri.getScheme())) && uri.getPath() == null ? "/" : "") + sessionURLPrefix + id + url.substring(suffix);
    }
    @Override
	public String encodeRedirectURL(String url) {
        return encodeURL(url);
    }
    @Override
    @Deprecated
	public String encodeUrl(String url) {
        return encodeURL(url);
    }
    @Override
    @Deprecated
	public String encodeRedirectUrl(String url) {
        return encodeRedirectURL(url);
    }
    @Override
	public void sendError(int sc) throws IOException {
        sendError(sc, null);
    }
    @Override
	public void sendError(int code, String message) throws IOException {
		if (isIncluding()) {
			return;
		}
		if (isCommitted()) {
            code=-1;
        }
        
		switch (code) {
            case -1:
                _channel.abort(new IOException());
                return;
            case 102:
                sendProcessing();
                return;
            default:
        }

		if (isCommitted()) {
			LOG.warn("cannot sendError(" + code + ", " + message + ") response already committed");
		}

        resetBuffer();
		_characterEncoding = null;
		setHeader(HttpHeader.EXPIRES, null);
		setHeader(HttpHeader.LAST_MODIFIED, null);
		setHeader(HttpHeader.CACHE_CONTROL, null);
		setHeader(HttpHeader.CONTENT_TYPE, null);
		setHeader(HttpHeader.CONTENT_LENGTH, null);

        _outputType = OutputType.NONE;
        setStatus(code);
        _reason=message;

        Request request = _channel.getRequest();
        Throwable cause = (Throwable)request.getAttribute(Dispatcher.ERROR_EXCEPTION);
		if (message == null) {
			message = cause == null ? HttpStatus.getMessage(code) : cause.toString();
		}

        // If we are allowed to have a body
        if (code!=SC_NO_CONTENT &&
            code!=SC_NOT_MODIFIED &&
            code!=SC_PARTIAL_CONTENT &&
				code >= SC_OK) {
            ErrorHandler error_handler = ErrorHandler.getErrorHandler(_channel.getServer(),request.getContext()==null?null:request.getContext().getContextHandler());
			if (error_handler != null) {
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,new Integer(code));
                request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
                request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,request.getServletName());
                error_handler.handle(null,_channel.getRequest(),_channel.getRequest(),this );
			} else {
				setHeader(HttpHeader.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
                setContentType(MimeTypes.Type.TEXT_HTML_8859_1.toString());
				try (ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(2048);) {
                    message=StringUtil.sanitizeXmlString(message);
                    String uri= request.getRequestURI();
                    uri=StringUtil.sanitizeXmlString(uri);

                    writer.write("<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html;charset=ISO-8859-1\"/>\n");
                    writer.write("<title>Error ");
                    writer.write(Integer.toString(code));
                    writer.write(' ');
					if (message == null) {
						writer.write(message);
					}
                    writer.write("</title>\n</head>\n<body>\n<h2>HTTP ERROR: ");
                    writer.write(Integer.toString(code));
                    writer.write("</h2>\n<p>Problem accessing ");
                    writer.write(uri);
                    writer.write(". Reason:\n<pre>    ");
                    writer.write(message);
                    writer.write("</pre>");
                    writer.write("</p>\n<hr />");

                    getHttpChannel().getHttpConfiguration().writePoweredBy(writer,null,"<hr/>");
                    writer.write("\n</body>\n</html>\n");

                    writer.flush();
                    setContentLength(writer.size());
					try (ServletOutputStream outputStream = getOutputStream()) {
                        writer.writeTo(outputStream);
                        writer.destroy();
                    }
                }
            }
		} else if (code != SC_PARTIAL_CONTENT) {
            // TODO work out why this is required?
            _channel.getRequest().getHttpFields().remove(HttpHeader.CONTENT_TYPE);
            _channel.getRequest().getHttpFields().remove(HttpHeader.CONTENT_LENGTH);
            _characterEncoding=null;
            _mimeType=null;
        }

        closeOutput();
    }
	@Override
	public void sendRedirect(String location) throws IOException {// 实现重定向的接口
        sendRedirect(HttpServletResponse.SC_MOVED_TEMPORARILY, location);
    }
    @Override
	public void setDateHeader(String name, long date) {
		if (!isIncluding()) {
			_fields.putDateField(name, date);
		}
    }
    @Override
	public void addDateHeader(String name, long date) {
		if (!isIncluding()) {
			_fields.addDateField(name, date);
		}
    }
    @Override
	public void setHeader(String name, String value) {
		if (HttpHeader.CONTENT_TYPE.is(name)) {
			setContentType(value);
		} else {
			if (isIncluding()) {
				if (name.startsWith(SET_INCLUDE_HEADER_PREFIX)) {
					name = name.substring(SET_INCLUDE_HEADER_PREFIX.length());
				} else {
					return;
				}
           }
           _fields.put(name, value);
			if (HttpHeader.CONTENT_LENGTH.is(name)) {
				if (value == null) {
					_contentLength = -1l;
				} else {
					_contentLength = Long.parseLong(value);
				}
           }
       }
   }
    @Override
    public void addHeader(String name, String value) {
        if (isIncluding()) {
 			if (name.startsWith(SET_INCLUDE_HEADER_PREFIX)) {
 				name = name.substring(SET_INCLUDE_HEADER_PREFIX.length());
 			} else {
 				return;
 			}
        }
        if (HttpHeader.CONTENT_TYPE.is(name)) {
            setContentType(value);
            return;
        }
        if (HttpHeader.CONTENT_LENGTH.is(name)) {
            setHeader(name,value);
            return;
        }
        _fields.add(name, value);
    }
    @Override
    public void setIntHeader(String name, int value) {
        if (!isIncluding()) {
            _fields.putLongField(name, value);
            if (HttpHeader.CONTENT_LENGTH.is(name))
                _contentLength = value;
        }
    }
    @Override
    public void addIntHeader(String name, int value) {
        if (!isIncluding()) {
            _fields.add(name, Integer.toString(value));
            if (HttpHeader.CONTENT_LENGTH.is(name))
                _contentLength = value;
        }
    }
    @Override
 	public void setStatus(int sc) {
 		if (sc <= 0) {
 			throw new IllegalArgumentException();
 		}
 		if (!isIncluding()) {
            _status = sc;
            _reason = null;
        }
    }
    @Override
    @Deprecated
    public void setStatus(int sc, String sm) {
        setStatusWithReason(sc,sm);
    }
    @Override
    public int getStatus() {
        return _status;
    }
	@Override
	public String getHeader(String name) {
		return _fields.get(name);
	}
    @Override
	public Collection<String> getHeaderNames() {
		final HttpFields fields = _fields;
		return fields.getFieldNamesCollection();
	}
	@Override
	public Collection<String> getHeaders(String name) {
		final HttpFields fields = _fields;
		Collection<String> i = fields.getValuesList(name);
		if (i == null)
			return Collections.emptyList();
		return i;
	}
	// ****************************************************************************************
	// 下面是实现的接口函数:ServletResponse
	@Override
	public void setCharacterEncoding(String encoding) {
		setCharacterEncoding(encoding, true);
	}
	@Override
	public String getCharacterEncoding() {
		if (_characterEncoding == null) {
			_characterEncoding = StringUtil.__ISO_8859_1;
		}
		return _characterEncoding;
	}
	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		if (_outputType == OutputType.WRITER) {
			throw new IllegalStateException("WRITER");
		}
		_outputType = OutputType.STREAM;
		return _out;
	}
	@Override
	public PrintWriter getWriter() throws IOException {// 用于JETTY响应发送数据

		if (_outputType == OutputType.STREAM) {// 默认为NONE
			throw new IllegalStateException("STREAM");
		}

		if (_outputType == OutputType.NONE) {

			String encoding = _characterEncoding;// 编码方式

			if (encoding == null) {// 表示没有设置

				if (_mimeType != null && _mimeType.isCharsetAssumed()) {// 是否指定了编码
					encoding = _mimeType.getCharsetString();
				} else {
					encoding = MimeTypes.inferCharsetFromContentType(_contentType);
					if (encoding == null) {// 表示采用默认的编码方式
						encoding = StringUtil.__ISO_8859_1;
					}
					setCharacterEncoding(encoding, false);
				}
			}

			// 编码方式获取途径:
			// 1、MIME中获取: _mimeType.getCharsetString()
			// 2、从CONTENT_TYPE中获取: MimeTypes.inferCharsetFromContentType(_contentType)
			// 3、默认: StringUtil.__ISO_8859_1

			Locale locale = getLocale();

			if (_writer != null && _writer.isFor(locale, encoding)) {
				_writer.reopen();
			} else {

				// 1、字节流
				// 2、字符流
				// 3、打印流

				// Iso88591HttpWriter、Utf8HttpWriter、EncodingHttpWriter: HttpWriter

				if (StringUtil.__ISO_8859_1.equalsIgnoreCase(encoding)) {
					_writer = new ResponseWriter(new Iso88591HttpWriter(_out), locale, encoding);// 每次都从新建一个WRITER
				} else if (StringUtil.__UTF8.equalsIgnoreCase(encoding)) {
					_writer = new ResponseWriter(new Utf8HttpWriter(_out), locale, encoding);
				} else {
					_writer = new ResponseWriter(new EncodingHttpWriter(_out, encoding), locale, encoding);
				}
			}
			_outputType = OutputType.WRITER;
		}
		return _writer;
	}
	@Override
	public void setContentLength(int len) {
		if (isCommitted() || isIncluding()) {
			return;
		}
		_contentLength = len;
		if (_contentLength > 0) {
			long written = _out.getWritten();
			if (written > len) {
				throw new IllegalArgumentException("setContentLength(" + len + ") when already written " + written);
			}
			_fields.putLongField(HttpHeader.CONTENT_LENGTH, len);
			if (isAllContentWritten(written)) {
				try {
					closeOutput();
				} catch (IOException e) {
					throw new RuntimeIOException(e);
				}
			}
		} else if (_contentLength == 0) {
			long written = _out.getWritten();
			if (written > 0)
				throw new IllegalArgumentException("setContentLength(0) when already written " + written);
			_fields.put(HttpHeader.CONTENT_LENGTH, "0");
		} else
			_fields.remove(HttpHeader.CONTENT_LENGTH);
	}
	@Override
	public void setContentLengthLong(long length) {
		setLongContentLength(length);
	}
	@Override
	public String getContentType() {
		return _contentType;
	}
	@Override
	public void setContentType(String contentType) {// 设置HTTP响应的内容格式
		if (isCommitted() || isIncluding()) {
			return;
		}
		if (contentType == null) {
			if (isWriting() && _characterEncoding != null) {
				throw new IllegalSelectorException();
			}
			if (_locale == null) {
				_characterEncoding = null;
			}
			_mimeType = null;
			_contentType = null;
			_fields.remove(HttpHeader.CONTENT_TYPE);
		} else {
			_contentType = contentType;
			_mimeType = MimeTypes.CACHE.get(contentType);
			String charset;
			if (_mimeType != null && _mimeType.getCharset() != null && !_mimeType.isCharsetAssumed()) {
				charset = _mimeType.getCharsetString();
			} else {
				charset = MimeTypes.getCharsetFromContentType(contentType);
			}

			if (charset == null) {
				if (_characterEncoding != null) {
					_contentType = contentType + ";charset=" + _characterEncoding;
					_mimeType = null;
				}
			} else if (isWriting() && !charset.equalsIgnoreCase(_characterEncoding)) {
				_mimeType = null;
				_contentType = MimeTypes.getContentTypeWithoutCharset(_contentType);
				if (_characterEncoding != null) {
					_contentType = _contentType + ";charset=" + _characterEncoding;
				}
			} else {
				_characterEncoding = charset;
				_explicitEncoding = true;
			}

			if (HttpGenerator.__STRICT || _mimeType == null) {
				_fields.put(HttpHeader.CONTENT_TYPE, _contentType);
			} else {
				_contentType = _mimeType.asString();
				_fields.put(_mimeType.getContentTypeField());
			}
		}
	}
	@Override
	public void setBufferSize(int size) {
		if (isCommitted() || getContentCount() > 0) {
			throw new IllegalStateException("cannot set buffer size on committed response");
		}
		if (size <= 0) {
			size = __MIN_BUFFER_SIZE;
		}
		_out.setBufferSize(size);
	}
	@Override
	public int getBufferSize() {
		return _out.getBufferSize();
	}
	@Override
	public void flushBuffer() throws IOException {
		if (!_out.isClosed())
			_out.flush();
	}
	@Override
	public void resetBuffer() {
		if (isCommitted()) {
			throw new IllegalStateException("cannot reset buffer on committed response");
		}
		_out.resetBuffer();
	}
	@Override
	public boolean isCommitted() {
		return _channel.isCommitted();
	}
	@Override
	public void reset() {
		resetForForward();
		_status = 200;
		_reason = null;
		_contentLength = -1;
		_fields.clear();

		String connection = _channel.getRequest().getHeader(HttpHeader.CONNECTION.asString());

		if (connection != null) {
			for (String value : StringUtil.csvSplit(null, connection, 0, connection.length())) {
				HttpHeaderValue cb = HttpHeaderValue.CACHE.get(value);

				if (cb != null) {
					switch (cb) {
					case CLOSE:
						_fields.put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE.toString());
						break;

					case KEEP_ALIVE:
						if (HttpVersion.HTTP_1_0.is(_channel.getRequest().getProtocol()))
							_fields.put(HttpHeader.CONNECTION, HttpHeaderValue.KEEP_ALIVE.toString());
						break;
					case TE:
						_fields.put(HttpHeader.CONNECTION, HttpHeaderValue.TE.toString());
						break;
					default:
					}
				}
			}
		}
	}
	@Override
	public void setLocale(Locale locale) {
		if (locale == null || isCommitted() || isIncluding()) {
			return;
		}
		_locale = locale;
		_fields.put(HttpHeader.CONTENT_LANGUAGE, locale.toString().replace('_', '-'));

		if (_outputType != OutputType.NONE) {
			return;
		}

		if (_channel.getRequest().getContext() == null) {
			return;
		}

		String charset = _channel.getRequest().getContext().getContextHandler().getLocaleEncoding(locale);

		if (charset != null && charset.length() > 0 && !_explicitEncoding) {
			setCharacterEncoding(charset, false);
		}
	}
	@Override
	public Locale getLocale() {
		if (_locale == null) {
			return Locale.getDefault();
		}
		return _locale;
	}

	// ****************************************************************************************
	public void addSetCookie(final String name, final String value, final String domain, final String path,
			final long maxAge, final String comment, final boolean isSecure, final boolean isHttpOnly, int version) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("Bad cookie name");
		}
		StringBuilder buf = __cookieBuilder.get();
		buf.setLength(0);

		// name is checked for legality by servlet spec, but can also be passed
		// directly so check again for quoting
		boolean quote_name = isQuoteNeededForCookie(name);
		quoteOnlyOrAppend(buf, name, quote_name);

		buf.append('=');

		// remember name= part to look for other matching set-cookie
		String name_equals = buf.toString();

		// append the value
		boolean quote_value = isQuoteNeededForCookie(value);
		quoteOnlyOrAppend(buf, value, quote_value);

		// look for domain and path fields and check if they need to be quoted
		boolean has_domain = domain != null && domain.length() > 0;
		boolean quote_domain = has_domain && isQuoteNeededForCookie(domain);
		boolean has_path = path != null && path.length() > 0;
		boolean quote_path = has_path && isQuoteNeededForCookie(path);

		// Upgrade the version if we have a comment or we need to quote
		// value/path/domain or if they were already quoted
		if (version == 0 && (comment != null || quote_name || quote_value || quote_domain || quote_path
				|| QuotedStringTokenizer.isQuoted(name) || QuotedStringTokenizer.isQuoted(value)
				|| QuotedStringTokenizer.isQuoted(path) || QuotedStringTokenizer.isQuoted(domain)))
			version = 1;

		// Append version
		if (version == 1) {
			buf.append(";Version=1");
		} else if (version > 1) {
			buf.append(";Version=").append(version);
		}

		// Append path
		if (has_path) {
			buf.append(";Path=");
			quoteOnlyOrAppend(buf, path, quote_path);
		}

		// Append domain
		if (has_domain) {
			buf.append(";Domain=");
			quoteOnlyOrAppend(buf, domain, quote_domain);
		}

		// Handle max-age and/or expires
		if (maxAge >= 0) {
			// Always use expires
			// This is required as some browser (M$ this means you!) don't
			// handle max-age even with v1 cookies
			buf.append(";Expires=");
			if (maxAge == 0)
				buf.append(__01Jan1970_COOKIE);
			else
				DateGenerator.formatCookieDate(buf, System.currentTimeMillis() + 1000L * maxAge);

			// for v1 cookies, also send max-age
			if (version >= 1) {
				buf.append(";Max-Age=");
				buf.append(maxAge);
			}
		}

		// add the other fields
		if (isSecure) {
			buf.append(";Secure");
		}
		if (isHttpOnly) {
			buf.append(";HttpOnly");
		}
		if (comment != null) {
			buf.append(";Comment=");
			quoteOnlyOrAppend(buf, comment, isQuoteNeededForCookie(comment));
		}

		// remove any existing set-cookie fields of same name
		Iterator<HttpField> i = _fields.iterator();
		while (i.hasNext()) {
			HttpField field = i.next();
			if (field.getHeader() == HttpHeader.SET_COOKIE) {
				String val = field.getValue();
				if (val != null && val.startsWith(name_equals)) {
					// existing cookie has same name, does it also match domain
					// and path?
					if (((!has_domain && !val.contains("Domain")) || (has_domain && val.contains(domain)))
							&& ((!has_path && !val.contains("Path")) || (has_path && val.contains(path)))) {
						i.remove();
					}
				}
			}
		}

		// add the set cookie
		_fields.add(HttpHeader.SET_COOKIE.toString(), buf.toString());

		// Expire responses with set-cookie headers so they do not get cached.
		_fields.put(__EXPIRES_01JAN1970);
	}
	private static boolean isQuoteNeededForCookie(String s) {
		if (s == null || s.length() == 0) {
			return true;
		}
		if (QuotedStringTokenizer.isQuoted(s)) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (__COOKIE_DELIM.indexOf(c) >= 0) {
				return true;
			}
			if (c < 0x20 || c >= 0x7f) {
				throw new IllegalArgumentException("Illegal character in cookie value");
			}
		}
		return false;
	}
	private static void quoteOnlyOrAppend(StringBuilder buf, String s, boolean quote) {
		if (quote) {
			QuotedStringTokenizer.quoteOnly(buf, s);
		} else {
			buf.append(s);
		}
	}
    public void sendProcessing() throws IOException {
        if (_channel.isExpecting102Processing() && !isCommitted()) {
            _channel.sendResponse(HttpGenerator.PROGRESS_102_INFO, null, true);
        }
    }
	public void sendRedirect(int code, String location) throws IOException {// 实现重定向

		// CODE范围限制
		if ((code < HttpServletResponse.SC_MULTIPLE_CHOICES) || (code >= HttpServletResponse.SC_BAD_REQUEST)) {
			throw new IllegalArgumentException("not a 3xx redirect code");
		}

		if (isIncluding()) {// ?????
			return;
		}

		if (location == null) {
			throw new IllegalArgumentException();
		}

        if (!URIUtil.hasScheme(location)) {
        	
			StringBuilder buf = _channel.getRequest().getRootURL();// 获取根路径
            
            if (location.startsWith("/")) {// absolute in context
				location = URIUtil.canonicalPath(location);
			} else {// relative to request
				String path = _channel.getRequest().getRequestURI();
				String parent = (path.endsWith("/")) ? path : URIUtil.parentPath(path);
                location= URIUtil.canonicalPath(URIUtil.addPaths(parent,location));
				if (!location.startsWith("/")) {
					buf.append('/');
				}
            }
			if (location == null) {
				throw new IllegalStateException("path cannot be above root");
			}
            buf.append(location);
			location = buf.toString();
        }
        resetBuffer();
        setHeader(HttpHeader.LOCATION, location);
        setStatus(code);
        closeOutput();
    }
	public void setHeader(HttpHeader name, String value) {
		if (HttpHeader.CONTENT_TYPE == name) {
			setContentType(value);
		} else {
			if (isIncluding()) {
				return;
			}
            _fields.put(name, value);
			if (HttpHeader.CONTENT_LENGTH == name) {
				if (value == null) {
					_contentLength = -1l;
				} else {
					_contentLength = Long.parseLong(value);
				}
            }
        }
    }
    public void setStatusWithReason(int sc, String sm) {
		if (sc <= 0) {
			throw new IllegalArgumentException();
		}
        if (!isIncluding()) {
            _status = sc;
            _reason = sm;
        }
    }
    public boolean isWriting() {
        return _outputType == OutputType.WRITER;
    }
	public long getContentLength() {
        return _contentLength;
    }
	public boolean isAllContentWritten(long written) {
        return (_contentLength >= 0 && written >= _contentLength);
    }
	public void closeOutput() throws IOException {// 关闭输出
		switch (_outputType) {
            case WRITER:
                _writer.close();
				if (!_out.isClosed()) {
					_out.close();
				}
                break;
            case STREAM:
                getOutputStream().close();
                break;
            default:
                _out.close();
        }
    }
	public long getLongContentLength() {
        return _contentLength;
    }
	public void setLongContentLength(long len) {
		if (isCommitted() || isIncluding()) {
			return;
		}
        _contentLength = len;
        _fields.putLongField(HttpHeader.CONTENT_LENGTH.toString(), len);
    }
	private void setCharacterEncoding(String encoding, boolean explicit) {// 设置响应的编码方式
		if (isIncluding() || isWriting()) {// 表示包含数据或正在写
			return;
		}
		if (_outputType == OutputType.NONE && !isCommitted()) {
			if (encoding == null) {
				_explicitEncoding = false;
				if (_characterEncoding != null) {
                    _characterEncoding = null;
					if (_mimeType != null) {
						_mimeType = _mimeType.getBaseType();
						_contentType = _mimeType.asString();
                        _fields.put(_mimeType.getContentTypeField());
					} else if (_contentType != null) {
                        _contentType = MimeTypes.getContentTypeWithoutCharset(_contentType);
                        _fields.put(HttpHeader.CONTENT_TYPE, _contentType);
                    }
                }
			} else {
                _explicitEncoding = explicit;
				_characterEncoding = HttpGenerator.__STRICT ? encoding : StringUtil.normalizeCharset(encoding);
				if (_mimeType != null) {
					_contentType = _mimeType.getBaseType().asString() + ";charset=" + _characterEncoding;
                    _mimeType = MimeTypes.CACHE.get(_contentType);
					if (_mimeType == null || HttpGenerator.__STRICT) {
						_fields.put(HttpHeader.CONTENT_TYPE, _contentType);
					} else {
						_fields.put(_mimeType.getContentTypeField());
					}
				} else if (_contentType != null) {
                    _contentType = MimeTypes.getContentTypeWithoutCharset(_contentType) + ";charset=" + _characterEncoding;
                    _fields.put(HttpHeader.CONTENT_TYPE, _contentType);
                }
            }
        }
    }
	public void reset(boolean preserveCookies) {
		if (!preserveCookies) {
			reset();
		} else {
            ArrayList<String> cookieValues = new ArrayList<String>(5);
            Enumeration<String> vals = _fields.getValues(HttpHeader.SET_COOKIE.asString());
			while (vals.hasMoreElements()) {
				cookieValues.add(vals.nextElement());
			}
            reset();
			for (String v : cookieValues) {
				_fields.add(HttpHeader.SET_COOKIE, v);
			}
        }
    }
	public void resetForForward() {
        resetBuffer();
        _outputType = OutputType.NONE;
    }
    protected MetaData.Response newResponseMetaData() {
        return new MetaData.Response(_channel.getRequest().getHttpVersion(), getStatus(), getReason(), _fields, getLongContentLength());
    }
    public MetaData.Response getCommittedMetaData() {
        MetaData.Response meta = _channel.getCommittedMetaData();
		if (meta == null) {
			return newResponseMetaData();
		}
        return meta;
    }
	public String getReason() {
        return _reason;
    }
	public HttpFields getHttpFields() {
        return _fields;
    }
	public long getContentCount() {
        return _out.getWritten();
    }
    public void putHeaders(HttpContent content,long contentLength, boolean etag) {
        HttpField lm = content.getLastModified();
        if (lm != null) {
            _fields.put(lm);
		}
        if (contentLength == 0) {
            _fields.put(content.getContentLength());
            _contentLength=content.getContentLengthValue();
        } else if (contentLength > 0) {
            _fields.putLongField(HttpHeader.CONTENT_LENGTH, contentLength);
            _contentLength = contentLength;
        }
        HttpField ct = content.getContentType();
        if (ct != null) {
            _fields.put(ct);
            _contentType = ct.getValue();
            _characterEncoding = content.getCharacterEncoding();
            _mimeType = content.getMimeType();
        }
        HttpField ce=content.getContentEncoding();
        if (ce != null) {
            _fields.put(ce);
        }
        if (etag) {
            HttpField et = content.getETag();
			if (et != null) {
				_fields.put(et);
			}
        }
    }
    public static void putHeaders(HttpServletResponse response, HttpContent content, long contentLength, boolean etag) {   
        long lml = content.getResource().lastModified();
        if (lml >= 0) {
            response.setDateHeader(HttpHeader.LAST_MODIFIED.asString(), lml);
		}
		if (contentLength == 0) {
            contentLength = content.getContentLengthValue();
		}
        if (contentLength >= 0) {
            if (contentLength < Integer.MAX_VALUE) {
                response.setContentLength((int)contentLength);
            } else {
                response.setHeader(HttpHeader.CONTENT_LENGTH.asString(), Long.toString(contentLength));
			}
        }
        String ct = content.getContentTypeValue();
        if (ct != null && response.getContentType() == null) {
            response.setContentType(ct);
		}

		String ce = content.getContentEncodingValue();
		if (ce != null) {
            response.setHeader(HttpHeader.CONTENT_ENCODING.asString(), ce);
		}
        if (etag) {
            String et=content.getETagValue();
            if (et != null) {
                response.setHeader(HttpHeader.ETAG.asString(), et);
			}
        }
    }

	// ****************************************************************************************
	@Override
	public String toString() {
		return String.format("%s %d %s%n%s", _channel.getRequest().getHttpVersion(), _status,
				_reason == null ? "" : _reason, _fields);
	}
	// ****************************************************************************************
}
