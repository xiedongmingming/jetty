package org.eclipse.jetty.http;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class PreEncodedHttpField extends HttpField {

    private final static Logger LOG = Log.getLogger(PreEncodedHttpField.class);

    private final static HttpFieldPreEncoder[] __encoders;
    
	static {
        List<HttpFieldPreEncoder> encoders = new ArrayList<>();
		Iterator<HttpFieldPreEncoder> iter = ServiceLoader.load(HttpFieldPreEncoder.class, PreEncodedHttpField.class.getClassLoader()).iterator();
		while (iter.hasNext()) {
			try {
                encoders.add(iter.next());
			} catch (Error | RuntimeException e) {
                LOG.debug(e);
            }
        }
		if (encoders.size() == 0) {
			encoders.add(new Http1FieldPreEncoder());
		}
		LOG.debug("httpfield encoders loaded: {}", encoders);

        __encoders = encoders.toArray(new HttpFieldPreEncoder[encoders.size()]);
    }
    
    private final byte[][] _encodedField=new byte[2][];

	// *********************************************************************
	// 构造函数
	public PreEncodedHttpField(HttpHeader header, String name, String value) {

		// 第二个参数表示该域的名称

		super(header, name, value);
		for (HttpFieldPreEncoder e : __encoders) {
			_encodedField[e.getHttpVersion() == HttpVersion.HTTP_2 ? 1 : 0] = e.getEncodedField(header, header.asString(), value);
        }
    }
	public PreEncodedHttpField(HttpHeader header, String value) {

		// 第一个参数表示HTTP头中的一个域
		// 第二个参数表示该域对应的值

		this(header, header.asString(), value);
    }
	public PreEncodedHttpField(String name, String value) {
		this(null, name, value);
    }

	// *********************************************************************
	public void putTo(ByteBuffer bufferInFillMode, HttpVersion version) {
		bufferInFillMode.put(_encodedField[version == HttpVersion.HTTP_2 ? 1 : 0]);
    }
}