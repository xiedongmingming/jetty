package org.eclipse.jetty.http;

import java.nio.ByteBuffer;
import java.util.EnumSet;

import org.eclipse.jetty.util.ArrayTrie;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Trie;

public enum HttpHeaderValue {// 域值

    CLOSE("close"),
    CHUNKED("chunked"),
    GZIP("gzip"),
    IDENTITY("identity"),
    KEEP_ALIVE("keep-alive"),
    CONTINUE("100-continue"),
    PROCESSING("102-processing"),
    TE("TE"),
    BYTES("bytes"),
    NO_CACHE("no-cache"),
    UPGRADE("Upgrade"),
    UNKNOWN("::UNKNOWN::");

	public final static Trie<HttpHeaderValue> CACHE = new ArrayTrie<HttpHeaderValue>();
	static {
		for (HttpHeaderValue value : HttpHeaderValue.values()) {
			if (value != UNKNOWN) {
				CACHE.put(value.toString(), value);
			}
		}
    }

    private final String _string;
    private final ByteBuffer _buffer;

	HttpHeaderValue(String s) {
		_string = s;// 域值字符串
		_buffer = BufferUtil.toBuffer(s);// 域值字节数组BUFFER
    }

	// **************************************************************************************************
	public ByteBuffer toBuffer() {
        return _buffer.asReadOnlyBuffer();
    }
	public boolean is(String s) {
        return _string.equalsIgnoreCase(s);
    }
	public String asString() {
        return _string;
    }
	// **************************************************************************************************
    @Override
	public String toString() {
        return _string;
    }
	// **************************************************************************************************
	private static EnumSet<HttpHeader> __known = EnumSet.of(HttpHeader.CONNECTION, HttpHeader.TRANSFER_ENCODING, HttpHeader.CONTENT_ENCODING);

	public static boolean hasKnownValues(HttpHeader header) {
		if (header == null) {
			return false;
		}
        return __known.contains(header);
    }
	// **************************************************************************************************
}
