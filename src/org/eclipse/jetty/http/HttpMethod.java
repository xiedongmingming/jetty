package org.eclipse.jetty.http;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.ArrayTrie;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.Trie;

public enum HttpMethod {// HTTP请求方法
    GET,
    POST,
    HEAD,
    PUT,
    OPTIONS,
    DELETE,
    TRACE,
    CONNECT,
    MOVE,
    PROXY,
    PRI;

	// *******************************************************************************
	public static HttpMethod lookAheadGet(byte[] bytes, final int position, int limit) {

		int length = limit - position;

		if (length < 4) {
			return null;
		}

		switch (bytes[position]) {
            case 'G':
				if (bytes[position + 1] == 'E' && bytes[position + 2] == 'T' && bytes[position + 3] == ' ') {
					return GET;
				}
                break;
            case 'P':
            	if (bytes[position + 1] == 'O' && bytes[position + 2] == 'S' && bytes[position + 3] == 'T' && length >= 5 && bytes[position + 4] == ' ') {
					return POST;
				}
            	if (bytes[position + 1] == 'R' && bytes[position + 2] == 'O' && bytes[position + 3] == 'X' && length >= 6 && bytes[position + 4] == 'Y' && bytes[position + 5] == ' ') {
					return PROXY;
				}
				if (bytes[position + 1] == 'U' && bytes[position + 2] == 'T' && bytes[position + 3] == ' ') {
					return PUT;
				}
				if (bytes[position + 1] == 'R' && bytes[position + 2] == 'I' && bytes[position + 3] == ' ') {
					return PRI;
				}
				break;
			case 'H':
				if (bytes[position + 1] == 'E' && bytes[position + 2] == 'A' && bytes[position + 3] == 'D' && length >= 5 && bytes[position + 4] == ' ') {
					return HEAD;
				}
				break;
            case 'O':
            	if (bytes[position + 1] == 'P' && bytes[position + 2] == 'T' && bytes[position + 3] == 'I' && length >= 8
					&& bytes[position + 4] == 'O' && bytes[position + 5] == 'N' && bytes[position + 6] == 'S'
					&& bytes[position + 7] == ' ') {
            		return OPTIONS;
            	}
                break;
            case 'D':
            	if (bytes[position + 1] == 'E' && bytes[position + 2] == 'L' && bytes[position + 3] == 'E' && length >= 7
					&& bytes[position + 4] == 'T' && bytes[position + 5] == 'E' && bytes[position + 6] == ' ') {
            		return DELETE;
            	}
                break;
            case 'T':
            	if (bytes[position + 1] == 'R' && bytes[position + 2] == 'A' && bytes[position + 3] == 'C' && length >= 6
					&& bytes[position + 4] == 'E' && bytes[position + 5] == ' ') {
            		return TRACE;
            	}
                break;
            case 'C':
            	if (bytes[position + 1] == 'O' && bytes[position + 2] == 'N' && bytes[position + 3] == 'N' && length >= 8
					&& bytes[position + 4] == 'E' && bytes[position + 5] == 'C' && bytes[position + 6] == 'T'
					&& bytes[position + 7] == ' ') {
            		return CONNECT;
            	}
                break;
            case 'M':
            	if (bytes[position + 1] == 'O' && bytes[position + 2] == 'V' && bytes[position + 3] == 'E' && length >= 5
					&& bytes[position + 4] == ' ') {
            		return MOVE;
            	}
                break;
            default:
                break;
        }
        return null;
    }
	public static HttpMethod lookAheadGet(ByteBuffer buffer) {// 表示从参数中获取HTTP请求方法

		if (buffer.hasArray()) {
			return lookAheadGet(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.arrayOffset() + buffer.limit());
		}
        
        int l = buffer.remaining();
        if (l >= 4) {
            HttpMethod m = CACHE.getBest(buffer, 0, l);
            if (m != null) {
                int ml = m.asString().length();
				if (l > ml && buffer.get(buffer.position() + ml) == ' ') {
					return m;
				}
            }
        }
        return null;
    }

	// *******************************************************************************
    public final static Trie<HttpMethod> CACHE = new ArrayTrie<>();

    static {
		for (HttpMethod method : HttpMethod.values()) {
			CACHE.put(method.toString(), method);
		}
    }

    private final ByteBuffer _buffer;
    private final byte[] _bytes;

    HttpMethod() {
        _bytes = StringUtil.getBytes(toString());
        _buffer = ByteBuffer.wrap(_bytes);
    }

	// *******************************************************************************

    public byte[] getBytes() {
        return _bytes;
    }
    public boolean is(String s) {
        return toString().equalsIgnoreCase(s);
    }
    public ByteBuffer asBuffer() {
        return _buffer.asReadOnlyBuffer();
    }
    public String asString() {
        return toString();
    }
    public static HttpMethod fromString(String method) {
        return CACHE.get(method);
    }
}
