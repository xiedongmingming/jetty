package javax.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

public class ServletResponseWrapper implements ServletResponse {// 包裹器
	
	private ServletResponse response;// 被包裹的对象

	//***************************************************************
	public ServletResponseWrapper(ServletResponse response) {//构造器
	    if (response == null) {
			throw new IllegalArgumentException("response cannot be null");
	    }
	    this.response = response;
	}
	//***************************************************************
	public ServletResponse getResponse() {
		return this.response;
	}	
	public void setResponse(ServletResponse response) {
	    if (response == null) {
			throw new IllegalArgumentException("response cannot be null");
	    }
	    this.response = response;
	}
	//***************************************************************
	// 下面包裹接口的所有函数
    public void setCharacterEncoding(String charset) {
		this.response.setCharacterEncoding(charset);
    }
    public String getCharacterEncoding() {
		return this.response.getCharacterEncoding();
	}
    public ServletOutputStream getOutputStream() throws IOException {
		return this.response.getOutputStream();
    }  
	public PrintWriter getWriter() throws IOException {
		return this.response.getWriter();
	}
    public void setContentLength(int len) {
		this.response.setContentLength(len);
    }
    public void setContentLengthLong(long len) {
        this.response.setContentLengthLong(len);
    }
    public void setContentType(String type) {
		this.response.setContentType(type);
    }
    public String getContentType() {
		return this.response.getContentType();
    }
    public void setBufferSize(int size) {
		this.response.setBufferSize(size);
    }
    public int getBufferSize() {
		return this.response.getBufferSize();
    }
    public void flushBuffer() throws IOException {
		this.response.flushBuffer();
    }
    public boolean isCommitted() {
		return this.response.isCommitted();
    }
    public void reset() {
		this.response.reset();
    }
    public void resetBuffer() {
		this.response.resetBuffer();
    }
    public void setLocale(Locale loc) {
		this.response.setLocale(loc);
    }
	public Locale getLocale() {
		return this.response.getLocale();
    }

	// ***************************************************************
	public boolean isWrapperFor(ServletResponse wrapped) {// 是否包裹指定的对象

        if (response == wrapped) {
            return true;
        } else if (response instanceof ServletResponseWrapper) {
            return ((ServletResponseWrapper) response).isWrapperFor(wrapped);
        } else {
            return false;
        }
    }
	public boolean isWrapperFor(Class<?> wrappedType) {

		if (!ServletResponse.class.isAssignableFrom(wrappedType)) {// 判断是否是子接口
			throw new IllegalArgumentException("given class " + wrappedType.getName() + " not a subinterface of "
					+ ServletResponse.class.getName());
        }

		if (wrappedType.isAssignableFrom(response.getClass())) {// ?????
            return true;
        } else if (response instanceof ServletResponseWrapper) {
            return ((ServletResponseWrapper) response).isWrapperFor(wrappedType);
        } else {
            return false;
        }
    }
	// ***************************************************************
}





