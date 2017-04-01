package org.eclipse.jetty.server;

import java.io.IOException;

public class Utf8HttpWriter extends HttpWriter {// 对应编码格式的输出WRITER

	int _surrogate = 0;

	// ************************************************************************
	public Utf8HttpWriter(HttpOutput out) {
        super(out);
    }
	// ************************************************************************
    @Override
	public void write(char[] s, int offset, int length) throws IOException {// 需要实现的抽象方法

		// JAVA的字符类型CHAR占用2个字节(因为他是UNICODE编码)

		HttpOutput out = _out;// 父类的成员

		if (length == 0 && out.isAllContentWritten()) {// ??
			close();// 表示所有内容都写完了
            return;
        }

		while (length > 0) {

            _bytes.reset();

			int chars = length > MAX_OUTPUT_CHARS ? MAX_OUTPUT_CHARS : length;// 这一次待写入的字符数

			byte[] buffer = _bytes.getBuf();// 一次可以写入的最大字节数组

			int bytes = _bytes.getCount();

			if (bytes + chars > buffer.length) {
				chars = buffer.length - bytes;
			}

			for (int i = 0; i < chars; i++) {

				int code = s[offset + i];

				if (_surrogate == 0) {
					if (Character.isHighSurrogate((char) code)) {

						// 判断给定CHAR值是UNICODE高代理项代码单元(也称为高级代理项代码单元).这个值并不代表字符本身而是在UTF-16编码的补充的字符的表示被使用.

						_surrogate = code;
                        continue;
                    }
				} else if (Character.isLowSurrogate((char) code)) {
					code = Character.toCodePoint((char) _surrogate, (char) code);
				} else {
					code = _surrogate;
					_surrogate = 0;
                    i--;
                }
				if ((code & 0xffffff80) == 0) {
					if (bytes >= buffer.length) {
						chars = i;
                        break;
                    }
					buffer[bytes++] = (byte) (code);
				} else {
					if ((code & 0xfffff800) == 0) {
						if (bytes + 2 > buffer.length) {
							chars = i;
                            break;
                        }
						buffer[bytes++] = (byte) (0xc0 | (code >> 6));
						buffer[bytes++] = (byte) (0x80 | (code & 0x3f));
					} else if ((code & 0xffff0000) == 0) {
						if (bytes + 3 > buffer.length) {
							chars = i;
                            break;
                        }
						buffer[bytes++] = (byte) (0xe0 | (code >> 12));
						buffer[bytes++] = (byte) (0x80 | ((code >> 6) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | (code & 0x3f));
					} else if ((code & 0xff200000) == 0) {
						if (bytes + 4 > buffer.length) {
							chars = i;
                            break;
                        }
						buffer[bytes++] = (byte) (0xf0 | (code >> 18));
						buffer[bytes++] = (byte) (0x80 | ((code >> 12) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | ((code >> 6) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | (code & 0x3f));
					} else if ((code & 0xf4000000) == 0) {
						if (bytes + 5 > buffer.length) {
							chars = i;
                            break;
                        }
						buffer[bytes++] = (byte) (0xf8 | (code >> 24));
						buffer[bytes++] = (byte) (0x80 | ((code >> 18) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | ((code >> 12) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | ((code >> 6) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | (code & 0x3f));
					} else if ((code & 0x80000000) == 0) {
						if (bytes + 6 > buffer.length) {
							chars = i;
                            break;
                        }
						buffer[bytes++] = (byte) (0xfc | (code >> 30));
						buffer[bytes++] = (byte) (0x80 | ((code >> 24) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | ((code >> 18) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | ((code >> 12) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | ((code >> 6) & 0x3f));
						buffer[bytes++] = (byte) (0x80 | (code & 0x3f));
					} else {
						buffer[bytes++] = (byte) ('?');
                    }
					_surrogate = 0;
					if (bytes == buffer.length) {
						chars = i + 1;
                        break;
                    }
                }
            }
            _bytes.setCount(bytes);
            _bytes.writeTo(out);
			length -= chars;
			offset += chars;
        }
    }
	// ************************************************************************
}
