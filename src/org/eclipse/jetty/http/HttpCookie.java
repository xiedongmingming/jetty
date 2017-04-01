package org.eclipse.jetty.http;

import java.util.concurrent.TimeUnit;

public class HttpCookie {// 表示一个COOKIE的各个域

	// 会话COOKIE和持久COOKIE

    private final String _name;
    private final String _value;
    private final String _comment;
    private final String _domain;
    private final long _maxAge;
    private final String _path;
    private final boolean _secure;
    private final int _version;
    private final boolean _httpOnly;
    private final long _expiration;

	// *********************************************************************************************************
	public HttpCookie(String name, String value) {
        this(name, value, -1);
    }
	public HttpCookie(String name, String value, String domain, String path) {
        this(name, value, domain, path, -1, false, false);
    }
	public HttpCookie(String name, String value, long maxAge) {
        this(name, value, null, null, maxAge, false, false);
    }
	public HttpCookie(String name, String value, String domain, String path, long maxAge, boolean httpOnly, boolean secure) {
        this(name, value, domain, path, maxAge, httpOnly, secure, null, 0);
    }
	public HttpCookie(String name, String value, String domain, String path, long maxAge, boolean httpOnly, boolean secure, String comment, int version) {
        _name = name;
        _value = value;
		_domain = domain;// 默认为NULL
		_path = path;// 默认为NULL
		_maxAge = maxAge;// 默认为-1
		_httpOnly = httpOnly;// 默认为FALSE
		_secure = secure;// 默认为FALSE
		_comment = comment;// 默认为NULL
		_version = version;// 默认为0
        _expiration = maxAge < 0 ? -1 : System.nanoTime() + TimeUnit.SECONDS.toNanos(maxAge);
    }

	// *********************************************************************************************************
	public String getName() {
        return _name;
    }
	public String getValue() {
        return _value;
    }
	public String getComment() {
        return _comment;
    }
	public String getDomain() {
        return _domain;
    }
	public long getMaxAge() {
        return _maxAge;
    }
	public String getPath() {
        return _path;
    }
	public boolean isSecure() {
        return _secure;
    }
	public int getVersion() {
        return _version;
    }
	public boolean isHttpOnly() {
        return _httpOnly;
    }
	public boolean isExpired(long timeNanos) {
        return _expiration >= 0 && timeNanos >= _expiration;
    }

	// *********************************************************************************************************
	public String asString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getName()).append("=").append(getValue());
		if (getDomain() != null) {
			builder.append(";$Domain=").append(getDomain());
		}
		if (getPath() != null) {
			builder.append(";$Path=").append(getPath());
		}
        return builder.toString();
    }
	// *********************************************************************************************************
}
