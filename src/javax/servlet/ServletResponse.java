package javax.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

public interface ServletResponse {// 响应

	// 包括的东西:
	// CharacterEncoding
	// ContentType
	// OutputStream
	// Writer
	// ContentLength
	// BufferSize
	// Locale

	public void setCharacterEncoding(String charset);
	public String getCharacterEncoding();// 获取编码方式

	public ServletOutputStream getOutputStream() throws IOException;// 获取字节输出流

	public PrintWriter getWriter() throws IOException;// 获取字符打印流

    public void setContentLength(int len);
    public void setContentLengthLong(long len);

	public String getContentType();// 获取内容类型
    public void setContentType(String type);

	public void setBufferSize(int size);// 设置缓冲大小
    public int getBufferSize();

    public void flushBuffer() throws IOException;

    public void resetBuffer();

	public boolean isCommitted();// 用于标识对应的响应是否已经被响应了(如果已经被响应了就不在继续后面的HANDLER了)

	public void reset();// ????

    public void setLocale(Locale loc);
    public Locale getLocale();
}





