package org.eclipse.jetty.http;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.ArrayTrie;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Trie;

public enum HttpScheme {// 表示HTTP方案(目前支持这四种)
    HTTP("http"),
    HTTPS("https"),
    WS("ws"),
    WSS("wss");

	// ************************************************************************************************************
	public final static Trie<HttpScheme> CACHE = new ArrayTrie<HttpScheme>();

	static {
		for (HttpScheme version : HttpScheme.values()) {
			CACHE.put(version.asString(), version);
		}
    }

    private final String _string;

	private final ByteBuffer _buffer;// 由上面字符串生成的字节缓存

	HttpScheme(String s) {
		_string = s;
		_buffer = BufferUtil.toBuffer(s);
    }

	// ************************************************************************************************************
	public ByteBuffer asByteBuffer() {// 生成只读副本
        return _buffer.asReadOnlyBuffer();
    }
	public boolean is(String s) {// 不区分大小写
		return s != null && _string.equalsIgnoreCase(s);
    }
	public String asString() {
        return _string;
    }
	// ************************************************************************************************************
    @Override
	public String toString() {
        return _string;
    }
	// ************************************************************************************************************
}
