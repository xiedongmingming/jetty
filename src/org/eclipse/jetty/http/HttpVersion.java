package org.eclipse.jetty.http;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.ArrayTrie;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.Trie;

public enum HttpVersion {// HTTP版本相关

	// 表示HTTP的版本相关--目前共支持四个版本

	HTTP_0_9("HTTP/0.9", 9), HTTP_1_0("HTTP/1.0", 10), HTTP_1_1("HTTP/1.1", 11), HTTP_2("HTTP/2.0", 20);

	public final static Trie<HttpVersion> CACHE = new ArrayTrie<HttpVersion>();// ????

	static {
		for (HttpVersion version : HttpVersion.values()) {
			CACHE.put(version.toString(), version);
		}
    }

	public static HttpVersion lookAheadGet(byte[] bytes, int position, int limit) {// ????

		int length = limit - position;

		if (length < 9) {
			return null;
		}

		if (bytes[position + 4] == '/'
				&& bytes[position + 6] == '.'
				&& Character.isWhitespace((char) bytes[position + 8])// ????
				&& ((bytes[position] == 'H' && bytes[position + 1] == 'T' && bytes[position + 2] == 'T' && bytes[position + 3] == 'P') 
					|| (bytes[position] == 'h' && bytes[position + 1] == 't' && bytes[position + 2] == 't' && bytes[position + 3] == 'p'))) {
			switch (bytes[position + 5]) {
                case '1':
                	switch (bytes[position + 7]) {
                        case '0':
                            return HTTP_1_0;
                        case '1':
                            return HTTP_1_1;
                    }
                    break;
                case '2':
                	switch (bytes[position + 7]) {
                        case '0':
                            return HTTP_2;
                    }
                    break;
            }
        }

		return null;
    }

	public static HttpVersion lookAheadGet(ByteBuffer buffer) {// 表示从参数中找出HTTP协议版本信息
		if (buffer.hasArray()) {
			return lookAheadGet(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.arrayOffset() + buffer.limit());
		}
        return null;
    }

	// ***************************************************************************
	private final String _string;// 协议名称
	private final byte[] _bytes;// 协议名称字节数组
	private final ByteBuffer _buffer;// NIO中的缓存
	private final int _version;// 协议对应的版本号

	HttpVersion(String s, int version) {
		_string = s;
		_bytes = StringUtil.getBytes(s);
		_buffer = ByteBuffer.wrap(_bytes);
		_version = version;
    }
	// ***************************************************************************

	public byte[] toBytes() {
        return _bytes;
    }
	public ByteBuffer toBuffer() {
        return _buffer.asReadOnlyBuffer();
    }
	public int getVersion() {
        return _version;
    }

	public boolean is(String s) {// 判定是否是该协议
        return _string.equalsIgnoreCase(s);    
    }
	public String asString() {
        return _string;
    }
	public static HttpVersion fromString(String version) {
        return CACHE.get(version);
    }
	public static HttpVersion fromVersion(int version) {
		switch (version) {
		case 9:
			return HttpVersion.HTTP_0_9;
		case 10:
			return HttpVersion.HTTP_1_0;
		case 11:
			return HttpVersion.HTTP_1_1;
		case 20:
			return HttpVersion.HTTP_2;
		default:
			throw new IllegalArgumentException();
        }
    }

	@Override
	public String toString() {
		return _string;
	}
}
