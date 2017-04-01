package org.eclipse.jetty.server;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.jetty.util.ByteArrayOutputStream2;

public abstract class HttpWriter extends Writer {// 抽象类

	// 也是对最底层的字节流包装: HttpOutput
    
	public static final int MAX_OUTPUT_CHARS = 512;// 可以进行缓存的字符数

	final HttpOutput _out;// 包装的字节流

	final ByteArrayOutputStream2 _bytes;// 用于缓存字节数组(固定大小)
	final char[] _chars;// 缓存字符数组(固定大小)

	// ***********************************************************************************************
	public HttpWriter(HttpOutput out) {
        _out = out;
        _chars = new char[MAX_OUTPUT_CHARS];
        _bytes = new ByteArrayOutputStream2(MAX_OUTPUT_CHARS);   
    }
	// ***********************************************************************************************
	// 下面是继承的方法
    @Override
    public void close() throws IOException {
        _out.close();
    }
    @Override
    public void flush() throws IOException {
        _out.flush();
    }
    @Override
	public void write(String s, int offset, int length) throws IOException {

		while (length > MAX_OUTPUT_CHARS) {// 待发送的字符长度

			write(s, offset, MAX_OUTPUT_CHARS);// 一次只发送指定数量的数据(递归调用自身)

            offset += MAX_OUTPUT_CHARS;
            length -= MAX_OUTPUT_CHARS;
        }

		s.getChars(offset, offset + length, _chars, 0);

		write(_chars, 0, length);// 调用子类的实现方法
    }
    @Override
	public void write(char[] s, int offset, int length) throws IOException {// 需要子类实现
        throw new AbstractMethodError();
    }
	// ***********************************************************************************************
}
