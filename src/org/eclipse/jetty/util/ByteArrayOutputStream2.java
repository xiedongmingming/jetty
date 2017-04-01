package org.eclipse.jetty.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public class ByteArrayOutputStream2 extends ByteArrayOutputStream {

	// *********************************************************************************
	public ByteArrayOutputStream2() {
		super();
	}
	public ByteArrayOutputStream2(int size) {// 参数用于指定字节数组的大小
		super(size);
	}
	// *********************************************************************************
	public byte[] getBuf() {
		return buf;// 底层字节数组
	}
	public int getCount() {
		return count;// 有效字节数
	}
	public void setCount(int count) {
		this.count = count;
	}
	public void reset(int minSize) {
		reset();// 有效字节数清零
		if (buf.length < minSize) {
			buf = new byte[minSize];
        }
    }
	public void writeUnchecked(int b) {
		buf[count++] = (byte) b;
    }
	public String toString(Charset charset) {
        return new String(buf, 0, count, charset);
    }
	// *********************************************************************************
}
