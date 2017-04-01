package org.eclipse.jetty.server;

import java.io.IOException;

public class HttpInputOverHTTP extends HttpInput {

	// ************************************************************************************************************
	public HttpInputOverHTTP(HttpChannelState state) {
        super(state);
    }
	// ************************************************************************************************************

    @Override
	protected void produceContent() throws IOException {
        ((HttpConnection)getHttpChannelState().getHttpChannel().getEndPoint().getConnection()).fillAndParseForContent();
    }
    @Override
	protected void blockForContent() throws IOException {
        ((HttpConnection)getHttpChannelState().getHttpChannel().getEndPoint().getConnection()).blockingReadFillInterested();
		try {
            super.blockForContent();
		} catch (Throwable e) {
            ((HttpConnection)getHttpChannelState().getHttpChannel().getEndPoint().getConnection()).blockingReadException(e);
        }
    }
}
