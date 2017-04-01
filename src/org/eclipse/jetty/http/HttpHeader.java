package org.eclipse.jetty.http;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.ArrayTrie;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.Trie;

public enum HttpHeader {// 表示HTTP的头

	// ********************************************************************************
	// 通用域
	CONNECTION("Connection"),
    CACHE_CONTROL("Cache-Control"),
    DATE("Date"),
    PRAGMA("Pragma"),
    PROXY_CONNECTION ("Proxy-Connection"),
    TRAILER("Trailer"),
    TRANSFER_ENCODING("Transfer-Encoding"),
    UPGRADE("Upgrade"),
    VIA("Via"),
    WARNING("Warning"),
    NEGOTIATE("Negotiate"),

	// ********************************************************************************
	// 实体域
    ALLOW("Allow"),
    CONTENT_ENCODING("Content-Encoding"),
    CONTENT_LANGUAGE("Content-Language"),
    CONTENT_LENGTH("Content-Length"),
    CONTENT_LOCATION("Content-Location"),
    CONTENT_MD5("Content-MD5"),
    CONTENT_RANGE("Content-Range"),
    CONTENT_TYPE("Content-Type"),
    EXPIRES("Expires"),
    LAST_MODIFIED("Last-Modified"),

	// ********************************************************************************
	// 请求域
    ACCEPT("Accept"),
    ACCEPT_CHARSET("Accept-Charset"),
    ACCEPT_ENCODING("Accept-Encoding"),
    ACCEPT_LANGUAGE("Accept-Language"),
    AUTHORIZATION("Authorization"),
    EXPECT("Expect"),
    FORWARDED("Forwarded"),
    FROM("From"),
    HOST("Host"),
    IF_MATCH("If-Match"),
    IF_MODIFIED_SINCE("If-Modified-Since"),
    IF_NONE_MATCH("If-None-Match"),
    IF_RANGE("If-Range"),
    IF_UNMODIFIED_SINCE("If-Unmodified-Since"),
    KEEP_ALIVE("Keep-Alive"),
    MAX_FORWARDS("Max-Forwards"),
    PROXY_AUTHORIZATION("Proxy-Authorization"),
    RANGE("Range"),
    REQUEST_RANGE("Request-Range"),
    REFERER("Referer"),
    TE("TE"),
    USER_AGENT("User-Agent"),
    X_FORWARDED_FOR("X-Forwarded-For"),
    X_FORWARDED_PROTO("X-Forwarded-Proto"),
    X_FORWARDED_SERVER("X-Forwarded-Server"),
    X_FORWARDED_HOST("X-Forwarded-Host"),

	// ********************************************************************************
	// 响应域
    ACCEPT_RANGES("Accept-Ranges"),
    AGE("Age"),
    ETAG("ETag"),
	LOCATION("Location"), // HTTP响应头中的域
    PROXY_AUTHENTICATE("Proxy-Authenticate"),
    RETRY_AFTER("Retry-After"),
    SERVER("Server"),
    SERVLET_ENGINE("Servlet-Engine"),
    VARY("Vary"),
    WWW_AUTHENTICATE("WWW-Authenticate"),

	// ********************************************************************************
	// 其它域
    COOKIE("Cookie"),
    SET_COOKIE("Set-Cookie"),
    SET_COOKIE2("Set-Cookie2"),
    MIME_VERSION("MIME-Version"),
    IDENTITY("identity"),
    X_POWERED_BY("X-Powered-By"),
    HTTP2_SETTINGS("HTTP2-Settings"),
    STRICT_TRANSPORT_SECURITY("Strict-Transport-Security"),

	// ********************************************************************************
	// HTTP2域
    C_METHOD(":method"),
    C_SCHEME(":scheme"),
    C_AUTHORITY(":authority"),
    C_PATH(":path"),
    C_STATUS(":status"),
    
	// 未知域
    UNKNOWN("::UNKNOWN::");
	// ********************************************************************************

	public final static Trie<HttpHeader> CACHE = new ArrayTrie<>(560);// 用于快速查找

	static {
        for (HttpHeader header : HttpHeader.values()) {
			if (header != UNKNOWN) {
                if (!CACHE.put(header.toString(), header)) {
					throw new IllegalStateException();
				}
			}
		}
    }
    
	private final String _string;// 域名称
	private final byte[] _bytes;// 域名称对应的字节数组
	private final byte[] _bytesColonSpace;// 域名称加冒号和空格的字节数组
	private final ByteBuffer _buffer;// 域名称对应的字节数组BUFFER

    HttpHeader(String s) {
		_string = s;// 名称
        _bytes = StringUtil.getBytes(s);
        _bytesColonSpace = StringUtil.getBytes(s + ": ");
        _buffer = ByteBuffer.wrap(_bytes);
    }
	// ********************************************************************************
    public ByteBuffer toBuffer() {
        return _buffer.asReadOnlyBuffer();
    }
    public byte[] getBytes() {
        return _bytes;
    }
    public byte[] getBytesColonSpace() {
        return _bytesColonSpace;
    }
    public boolean is(String s) {
        return _string.equalsIgnoreCase(s);    
    }
    public String asString() {
        return _string;
    }
	// ********************************************************************************
	@Override
    public String toString() {
        return _string;
    }
	// ********************************************************************************
}

