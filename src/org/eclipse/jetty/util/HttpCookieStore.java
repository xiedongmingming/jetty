package org.eclipse.jetty.util;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class HttpCookieStore implements CookieStore {// 代表一个HTTP的COOKIE存储

	private final CookieStore delegate;// 用于存储COOKIE的

	public HttpCookieStore() {
		delegate = new CookieManager().getCookieStore();// 生成一个COOKIE
    }

	// *************************************************************
    @Override
	public void add(URI uri, HttpCookie cookie) {
        delegate.add(uri, cookie);
    }
    @Override
	public List<HttpCookie> get(URI uri) {
        return delegate.get(uri);
    }
    @Override
	public List<HttpCookie> getCookies() {
        return delegate.getCookies();
    }
    @Override
	public List<URI> getURIs() {
        return delegate.getURIs();
    }
    @Override
	public boolean remove(URI uri, HttpCookie cookie) {
        return delegate.remove(uri, cookie);
    }
    @Override
	public boolean removeAll() {
        return delegate.removeAll();
    }
	// *************************************************************

	public static class Empty implements CookieStore {// 空实现
        @Override
		public void add(URI uri, HttpCookie cookie) {
        }
        @Override
		public List<HttpCookie> get(URI uri) {
            return Collections.emptyList();
        }
        @Override
		public List<HttpCookie> getCookies() {
            return Collections.emptyList();
        }
        @Override
		public List<URI> getURIs() {
            return Collections.emptyList();
        }
        @Override
		public boolean remove(URI uri, HttpCookie cookie) {
            return false;
        }
        @Override
		public boolean removeAll() {
            return false;
        }
    }
}
