package org.eclipse.jetty.server;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

public class EncodingHttpWriter extends HttpWriter {//// 对应编码格式的输出WRITER
    final Writer _converter;

	public EncodingHttpWriter(HttpOutput out, String encoding) {
        super(out);
		try {
            _converter = new OutputStreamWriter(_bytes, encoding);
		} catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
	public void write(char[] s, int offset, int length) throws IOException {
        HttpOutput out = _out;
		if (length == 0 && out.isAllContentWritten()) {
            out.close();
            return;
        }
            
		while (length > 0) {
            _bytes.reset();
            int chars = length>MAX_OUTPUT_CHARS?MAX_OUTPUT_CHARS:length;

            _converter.write(s, offset, chars);
            _converter.flush();
            _bytes.writeTo(out);
            length-=chars;
            offset+=chars;
        }
    }
}
