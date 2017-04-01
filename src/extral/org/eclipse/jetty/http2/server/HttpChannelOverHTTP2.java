package extral.org.eclipse.jetty.http2.server;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpGenerator;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import extral.org.eclipse.jetty.http2.IStream;
import extral.org.eclipse.jetty.http2.api.Stream;
import extral.org.eclipse.jetty.http2.frames.DataFrame;
import extral.org.eclipse.jetty.http2.frames.HeadersFrame;

public class HttpChannelOverHTTP2 extends HttpChannel {

    private static final Logger LOG = Log.getLogger(HttpChannelOverHTTP2.class);

    private static final HttpField SERVER_VERSION = new PreEncodedHttpField(HttpHeader.SERVER, HttpConfiguration.SERVER_VERSION);
    private static final HttpField POWERED_BY = new PreEncodedHttpField(HttpHeader.X_POWERED_BY, HttpConfiguration.SERVER_VERSION);

    private boolean _expect100Continue;
    private boolean _delayedUntilContent;

	public HttpChannelOverHTTP2(Connector connector, HttpConfiguration configuration, EndPoint endPoint,
			HttpTransportOverHTTP2 transport) {
        super(connector, configuration, endPoint, transport);
    }

    protected IStream getStream()
    {
        return getHttpTransport().getStream();
    }

    @Override
    public boolean isExpecting100Continue()
    {
        return _expect100Continue;
    }

    public Runnable onRequest(HeadersFrame frame)
    {
        try
        {
            MetaData.Request request = (MetaData.Request)frame.getMetaData();
            HttpFields fields = request.getFields();

            // HTTP/2 sends the Host header as the :authority
            // pseudo-header, so we need to synthesize a Host header.
            if (!fields.contains(HttpHeader.HOST))
            {
                String authority = request.getURI().getAuthority();
                if (authority != null)
                {
                    // Lower-case to be consistent with other HTTP/2 headers.
                    fields.put("host", authority);
                }
            }

            _expect100Continue = fields.contains(HttpHeader.EXPECT, HttpHeaderValue.CONTINUE.asString());

            HttpFields response = getResponse().getHttpFields();
            if (getHttpConfiguration().getSendServerVersion())
                response.add(SERVER_VERSION);
            if (getHttpConfiguration().getSendXPoweredBy())
                response.add(POWERED_BY);

            onRequest(request);

            boolean endStream = frame.isEndStream();
            if (endStream)
                onRequestComplete();

            _delayedUntilContent = getHttpConfiguration().isDelayDispatchUntilContent() &&
                    !endStream && !_expect100Continue;

            if (LOG.isDebugEnabled())
            {
                Stream stream = getStream();
                LOG.debug("HTTP2 Request #{}/{}, delayed={}:{}{} {} {}{}{}",
                        stream.getId(), Integer.toHexString(stream.getSession().hashCode()),
                        _delayedUntilContent, System.lineSeparator(),
                        request.getMethod(), request.getURI(), request.getVersion(),
                        System.lineSeparator(), fields);
            }

            return _delayedUntilContent ? null : this;
        }
        catch (BadMessageException x)
        {
            onBadMessage(x.getCode(), x.getReason());
            return null;
        }
        catch (Throwable x)
        {
            onBadMessage(HttpStatus.INTERNAL_SERVER_ERROR_500, null);
            return null;
        }
    }

    public Runnable onPushRequest(MetaData.Request request)
    {
        try
        {
            onRequest(request);
            getRequest().setAttribute("org.eclipse.jetty.pushed", Boolean.TRUE);
            onRequestComplete();

            if (LOG.isDebugEnabled())
            {
                Stream stream = getStream();
                LOG.debug("HTTP2 PUSH Request #{}/{}:{}{} {} {}{}{}",
                        stream.getId(), Integer.toHexString(stream.getSession().hashCode()), System.lineSeparator(),
                        request.getMethod(), request.getURI(), request.getVersion(),
                        System.lineSeparator(), request.getFields());
            }

            return this;
        }
        catch (BadMessageException x)
        {
            onBadMessage(x.getCode(), x.getReason());
            return null;
        }
        catch (Throwable x)
        {
            onBadMessage(HttpStatus.INTERNAL_SERVER_ERROR_500, null);
            return null;
        }
    }

    @Override
    public HttpTransportOverHTTP2 getHttpTransport()
    {
        return (HttpTransportOverHTTP2)super.getHttpTransport();
    }

    @Override
    public void recycle()
    {
        _expect100Continue = false;
        _delayedUntilContent = false;
        super.recycle();
        getHttpTransport().recycle();
    }

    @Override
    protected void commit(MetaData.Response info)
    {
        super.commit(info);
        if (LOG.isDebugEnabled())
        {
            Stream stream = getStream();
            LOG.debug("HTTP2 Commit Response #{}/{}:{}{} {} {}{}{}",
                    stream.getId(), Integer.toHexString(stream.getSession().hashCode()), System.lineSeparator(), info.getVersion(), info.getStatus(), info.getReason(),
                    System.lineSeparator(), info.getFields());
        }
    }

    public Runnable requestContent(DataFrame frame, final Callback callback)
    {
        // We must copy the data since we do not know when the
        // application will consume the bytes (we queue them by
        // calling onContent()), and the parsing will continue
        // as soon as this method returns, eventually leading
        // to reusing the underlying buffer for more reads.
        final ByteBufferPool byteBufferPool = getByteBufferPool();
        ByteBuffer original = frame.getData();
        int length = original.remaining();
        final ByteBuffer copy = byteBufferPool.acquire(length, original.isDirect());
        BufferUtil.clearToFill(copy);
        copy.put(original);
        BufferUtil.flipToFlush(copy, 0);

        boolean handle = onContent(new HttpInput.Content(copy)
        {
            @Override
            public boolean isNonBlocking()
            {
                return callback.isNonBlocking();
            }

            @Override
            public void succeeded()
            {
                byteBufferPool.release(copy);
                callback.succeeded();
            }

            @Override
            public void failed(Throwable x)
            {
                byteBufferPool.release(copy);
                callback.failed(x);
            }
        });

        boolean endStream = frame.isEndStream();
        if (endStream)
            handle |= onRequestComplete();

        if (LOG.isDebugEnabled())
        {
            Stream stream = getStream();
            LOG.debug("HTTP2 Request #{}/{}: {} bytes of {} content, handle: {}",
                    stream.getId(),
                    Integer.toHexString(stream.getSession().hashCode()),
                    length,
                    endStream ? "last" : "some",
                    handle);
        }

        boolean delayed = _delayedUntilContent;
        _delayedUntilContent = false;

        return handle || delayed ? this : null;
    }

    /**
     * If the associated response has the Expect header set to 100 Continue,
     * then accessing the input stream indicates that the handler/servlet
     * is ready for the request body and thus a 100 Continue response is sent.
     *
     * @throws IOException if the InputStream cannot be created
     */
    @Override
    public void continue100(int available) throws IOException
    {
        // If the client is expecting 100 CONTINUE, then send it now.
        // TODO: consider using an AtomicBoolean ?
        if (isExpecting100Continue())
        {
            _expect100Continue = false;

            // is content missing?
            if (available == 0)
            {
                if (getResponse().isCommitted())
                    throw new IOException("Committed before 100 Continues");

                boolean committed = sendResponse(HttpGenerator.CONTINUE_100_INFO, null, false);
                if (!committed)
                    throw new IOException("Concurrent commit while trying to send 100-Continue");
            }
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s#%d", super.toString(), getStream().getId());
    }
}
