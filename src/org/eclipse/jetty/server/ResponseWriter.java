package org.eclipse.jetty.server;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.util.Formatter;
import java.util.Locale;

import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ResponseWriter extends PrintWriter {

	// 包装了PRINTWRITER

    private static final Logger LOG = Log.getLogger(ResponseWriter.class);

    private final static String __lineSeparator = System.getProperty("line.separator");
	private final static String __trueln = "true" + __lineSeparator;
	private final static String __falseln = "false" + __lineSeparator;
    
	private final HttpWriter _httpWriter;// 底层实现WRITER
	private final Locale _locale;// 本地环境
	private final String _encoding;// 对应的编码方式

	private IOException _ioException;// 输出过程中产生的异常
	private boolean _isClosed = false;// 该打印流是否关闭了

	private Formatter _formatter;// 用于格式化输出
	public ResponseWriter(HttpWriter httpWriter, Locale locale, String encoding) {
		super(httpWriter, false);// 第一个参数为字符输出流,第二个参数为是否自动刷新
		_httpWriter = httpWriter;// 对应的字符流
		_locale = locale;
		_encoding = encoding;
    }
	public boolean isFor(Locale locale, String encoding) {// 必须完全相同
		if (_locale == null && locale != null) {
			return false;
		}
		if (_encoding == null && encoding != null) {
			return false;
		}
        return _encoding.equalsIgnoreCase(encoding) && _locale.equals(locale);
    }
	protected void reopen() {
		synchronized (lock) {
			_isClosed = false;
            clearError();
			out = _httpWriter;// 父类字段关联
        }
    }
    @Override
	protected void clearError() {
		synchronized (lock) {
			_ioException = null;
            super.clearError();
        }
    }
    @Override
	public boolean checkError() {
		synchronized (lock) {
			return _ioException != null || super.checkError();
        }
    }
	private void setError(Throwable th) {
        super.setError();
		if (th instanceof IOException) {
			_ioException = (IOException) th;
		} else {
			_ioException = new IOException(String.valueOf(th));
			_ioException.initCause(th);// ????
        }
		if (LOG.isDebugEnabled()) {
			LOG.debug(th);
		}
    }
    @Override
	protected void setError() {
        setError(new IOException());
    }
	private void isOpen() throws IOException {// 表示该流是否打开
		if (_ioException != null) {
			throw new RuntimeIOException(_ioException);
		}
		if (_isClosed) {
			throw new EofException("stream closed");
		}
    }
    @Override
	public void flush() {
		try {
			synchronized (lock) {
				isOpen();// 会抛出异常的
                out.flush();
            }
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void close() {
		try {
			synchronized (lock) {
                out.close();
                _isClosed = true;
            }
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void write(int c) {
		try {
			synchronized (lock) {
                isOpen();
				out.write(c);// 对应的是字符流
            }
		} catch (InterruptedIOException ex) {
            LOG.debug(ex);
			Thread.currentThread().interrupt();// ????
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void write(char buf[], int off, int len) {
		try {
			synchronized (lock) {
                isOpen();
				out.write(buf, off, len);
            }
		} catch (InterruptedIOException ex) {
            LOG.debug(ex);
            Thread.currentThread().interrupt();
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void write(char buf[]) {
		this.write(buf, 0, buf.length);
    }
    @Override
	public void write(String s, int off, int len) {
		try {
			synchronized (lock) {
                isOpen();
				out.write(s, off, len);
            }
		} catch (InterruptedIOException ex) {
            LOG.debug(ex);
            Thread.currentThread().interrupt();
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void write(String s) {
		this.write(s, 0, s.length());
    }

	// *************************************************************
    @Override
	public void print(boolean b) {
		this.write(b ? "true" : "false");
    }
    @Override
	public void print(char c) {
        this.write(c);
    }
    @Override
	public void print(int i) {
        this.write(String.valueOf(i));
    }
    @Override
	public void print(long l) {
        this.write(String.valueOf(l));
    }
    @Override
	public void print(float f) {
        this.write(String.valueOf(f));
    }
    @Override
	public void print(double d) {
        this.write(String.valueOf(d));
    }
    @Override
	public void print(char s[]) {
        this.write(s);
    }
    @Override
	public void print(String s) {
		if (s == null) {
			s = "null";
		}
        this.write(s);
    }
    @Override
	public void print(Object obj) {
        this.write(String.valueOf(obj));
    }
    @Override
	public void println() {
		try {
			synchronized (lock) {
                isOpen();
                out.write(__lineSeparator);
            }
		} catch (InterruptedIOException ex) {
            LOG.debug(ex);
            Thread.currentThread().interrupt();
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void println(boolean b) {
		println(b ? __trueln : __falseln);
    }
    @Override
	public void println(char c) {
		try {
			synchronized (lock) {
                isOpen();
                out.write(c);
            }
		} catch (InterruptedIOException ex) {
            LOG.debug(ex);
            Thread.currentThread().interrupt();
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void println(int x) {
        this.println(String.valueOf(x));
    }
    @Override
	public void println(long x) {
        this.println(String.valueOf(x));
    }
    @Override
	public void println(float x) {
        this.println(String.valueOf(x));
    }
    @Override
	public void println(double x) {
        this.println(String.valueOf(x));
    }
    @Override
	public void println(char s[]) {
		try {
			synchronized (lock) {
                isOpen();
				out.write(s, 0, s.length);
                out.write(__lineSeparator);
            }
		} catch (InterruptedIOException ex) {
            LOG.debug(ex);
            Thread.currentThread().interrupt();
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void println(String s) {
		if (s == null) {
			s = "null";
		}
		try {
			synchronized (lock) {
                isOpen();
				out.write(s, 0, s.length());
                out.write(__lineSeparator);
            }
		} catch (InterruptedIOException ex) {
            LOG.debug(ex);
            Thread.currentThread().interrupt();
		} catch (IOException ex) {
            setError(ex);
        }
    }
    @Override
	public void println(Object x) {
        this.println(String.valueOf(x));
    }
    @Override
	public PrintWriter printf(String format, Object... args) {
		return format(_locale, format, args);
    }
    @Override
	public PrintWriter printf(Locale l, String format, Object... args) {
		return format(l, format, args);
    }
    @Override
	public PrintWriter format(String format, Object... args) {
		return format(_locale, format, args);
    }
    @Override
	public PrintWriter format(Locale l, String format, Object... args) {
		try {
			synchronized (lock) {
                isOpen();
				if ((_formatter == null) || (_formatter.locale() != l)) {
					_formatter = new Formatter(this, l);
				}
                _formatter.format(l, format, args);
            }
		} catch (InterruptedIOException ex) {
            LOG.debug(ex);
            Thread.currentThread().interrupt();
		} catch (IOException ex) {
            setError(ex);
        }
        return this;
    }
}