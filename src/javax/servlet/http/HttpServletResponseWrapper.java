package javax.servlet.http;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletResponseWrapper;

public class HttpServletResponseWrapper extends ServletResponseWrapper implements HttpServletResponse {

	// ***************************************************************************
    public HttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
    }
	// ***************************************************************************
    private HttpServletResponse _getHttpServletResponse() {
        return (HttpServletResponse) super.getResponse();
    }
	// ***************************************************************************
	// 下面是实现的接口(需要底层的响应实现)
    @Override
    public void addCookie(Cookie cookie) {
        this._getHttpServletResponse().addCookie(cookie);
    }
    @Override
    public boolean containsHeader(String name) {
        return this._getHttpServletResponse().containsHeader(name);
    }
    @Override
    public String encodeURL(String url) {
        return this._getHttpServletResponse().encodeURL(url);
    }
    @Override
    public String encodeRedirectURL(String url) {
        return this._getHttpServletResponse().encodeRedirectURL(url);
    }
    @Override
    public String encodeUrl(String url) {
        return this._getHttpServletResponse().encodeUrl(url);
    }
    @Override
    public String encodeRedirectUrl(String url) {
        return this._getHttpServletResponse().encodeRedirectUrl(url);
    }
    @Override
    public void sendError(int sc, String msg) throws IOException {
        this._getHttpServletResponse().sendError(sc, msg);
    }
    @Override
    public void sendError(int sc) throws IOException {
        this._getHttpServletResponse().sendError(sc);
    }
    @Override
    public void sendRedirect(String location) throws IOException {
        this._getHttpServletResponse().sendRedirect(location);
    }
    @Override
    public void setDateHeader(String name, long date) {
        this._getHttpServletResponse().setDateHeader(name, date);
    }
    @Override
    public void addDateHeader(String name, long date) {
        this._getHttpServletResponse().addDateHeader(name, date);
    }
    @Override
    public void setHeader(String name, String value) {
        this._getHttpServletResponse().setHeader(name, value);
    }
    @Override
    public void addHeader(String name, String value) {
        this._getHttpServletResponse().addHeader(name, value);
    }
    @Override
    public void setIntHeader(String name, int value) {
        this._getHttpServletResponse().setIntHeader(name, value);
    }
    @Override
    public void addIntHeader(String name, int value) {
        this._getHttpServletResponse().addIntHeader(name, value);
    }
    @Override
    public void setStatus(int sc) {
        this._getHttpServletResponse().setStatus(sc);
    }
    @Override
    public void setStatus(int sc, String sm) {
        this._getHttpServletResponse().setStatus(sc, sm);
    }
    @Override
    public int getStatus() {
        return _getHttpServletResponse().getStatus();
    }
    @Override
    public String getHeader(String name) {
        return _getHttpServletResponse().getHeader(name);
	}
    @Override
    public Collection<String> getHeaders(String name) {
        return _getHttpServletResponse().getHeaders(name);
    }
    @Override
    public Collection<String> getHeaderNames() {
        return _getHttpServletResponse().getHeaderNames();
    }
	// ***************************************************************************
}
