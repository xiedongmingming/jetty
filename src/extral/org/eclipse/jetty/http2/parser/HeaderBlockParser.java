package extral.org.eclipse.jetty.http2.parser;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;

import extral.org.eclipse.jetty.http2.hpack.HpackDecoder;

public class HeaderBlockParser {
    private final ByteBufferPool byteBufferPool;
    private final HpackDecoder hpackDecoder;
    private ByteBuffer blockBuffer;

	public HeaderBlockParser(ByteBufferPool byteBufferPool, HpackDecoder hpackDecoder) {
        this.byteBufferPool = byteBufferPool;
        this.hpackDecoder = hpackDecoder;
    }

	public MetaData parse(ByteBuffer buffer, int blockLength) {
        // We must wait for the all the bytes of the header block to arrive.
        // If they are not all available, accumulate them.
        // When all are available, decode them.

        int accumulated = blockBuffer == null ? 0 : blockBuffer.position();
        int remaining = blockLength - accumulated;

		if (buffer.remaining() < remaining) {
			if (blockBuffer == null) {
                blockBuffer = byteBufferPool.acquire(blockLength, false);
                BufferUtil.clearToFill(blockBuffer);
            }
            blockBuffer.put(buffer);
            return null;
		} else {
            int limit = buffer.limit();
            buffer.limit(buffer.position() + remaining);
            ByteBuffer toDecode;
			if (blockBuffer != null) {
                blockBuffer.put(buffer);
                BufferUtil.flipToFlush(blockBuffer, 0);
                toDecode = blockBuffer;
			} else {
                toDecode = buffer;
            }

            MetaData result = hpackDecoder.decode(toDecode);

            buffer.limit(limit);

			if (blockBuffer != null) {
                byteBufferPool.release(blockBuffer);
                blockBuffer = null;
            }

            return result;
        }
    }
}
