package extral.org.eclipse.jetty.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.util.Callback;

import extral.org.eclipse.jetty.client.api.ContentProvider;
import extral.org.eclipse.jetty.client.api.Request;
import extral.org.eclipse.jetty.client.api.Response;
import extral.org.eclipse.jetty.client.api.Result;
import extral.org.eclipse.jetty.client.util.InputStreamContentProvider;

@SuppressWarnings("serial")
public class ProxyServlet extends AbstractProxyServlet {
    @Override
	protected void service(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
        final int requestId = getRequestId(request);

        String rewrittenTarget = rewriteTarget(request);

        if (_log.isDebugEnabled())
        {
            StringBuffer uri = request.getRequestURL();
			if (request.getQueryString() != null) {
				uri.append("?").append(request.getQueryString());
			}
        }

        if (rewrittenTarget == null)
        {
            onProxyRewriteFailed(request, response);
            return;
        }

        final Request proxyRequest = getHttpClient().newRequest(rewrittenTarget)
                .method(request.getMethod())
                .version(HttpVersion.fromString(request.getProtocol()));

        copyRequestHeaders(request, proxyRequest);

        addProxyHeaders(request, proxyRequest);

        final AsyncContext asyncContext = request.startAsync();
        // We do not timeout the continuation, but the proxy request
        asyncContext.setTimeout(0);
        proxyRequest.timeout(getTimeout(), TimeUnit.MILLISECONDS);

		if (hasContent(request)) {
			proxyRequest.content(proxyRequestContent(request, response, proxyRequest));
		}

        sendProxyRequest(request, response, proxyRequest);
    }

	protected ContentProvider proxyRequestContent(HttpServletRequest request, HttpServletResponse response,
			Request proxyRequest) throws IOException {
        return new ProxyInputStreamContentProvider(request, response, proxyRequest, request.getInputStream());
    }

    @Override
	protected Response.Listener newProxyResponseListener(HttpServletRequest request, HttpServletResponse response) {
        return new ProxyResponseListener(request, response);
    }

	protected void onResponseContent(HttpServletRequest request, HttpServletResponse response, Response proxyResponse,
			byte[] buffer, int offset, int length, Callback callback) {
		try {
            response.getOutputStream().write(buffer, offset, length);
            callback.succeeded();
		} catch (Throwable x) {
            callback.failed(x);
        }
    }

	public static class Transparent extends ProxyServlet {
        private final TransparentDelegate delegate = new TransparentDelegate(this);

        @Override
		public void init(ServletConfig config) throws ServletException {
            super.init(config);
            delegate.init(config);
        }

        @Override
		protected String rewriteTarget(HttpServletRequest request) {
            return delegate.rewriteTarget(request);
        }
    }

	protected class ProxyResponseListener extends Response.Listener.Adapter {
        private final HttpServletRequest request;
        private final HttpServletResponse response;

		protected ProxyResponseListener(HttpServletRequest request, HttpServletResponse response) {
            this.request = request;
            this.response = response;
        }

        @Override
		public void onBegin(Response proxyResponse) {
            response.setStatus(proxyResponse.getStatus());
        }

        @Override
		public void onHeaders(Response proxyResponse) {
            onServerResponseHeaders(request, response, proxyResponse);
        }

        @Override
		public void onContent(final Response proxyResponse, ByteBuffer content, final Callback callback) {
            byte[] buffer;
            int offset;
            int length = content.remaining();
			if (content.hasArray()) {
                buffer = content.array();
                offset = content.arrayOffset();
			} else {
                buffer = new byte[length];
                content.get(buffer);
                offset = 0;
            }

			onResponseContent(request, response, proxyResponse, buffer, offset, length, new Callback.Nested(callback) {
                @Override
				public void failed(Throwable x) {
                    super.failed(x);
                    proxyResponse.abort(x);
                }
            });
        }

        @Override
		public void onComplete(Result result) {
			if (result.isSucceeded()) {
				onProxyResponseSuccess(request, response, result.getResponse());
			} else {
				onProxyResponseFailure(request, response, result.getResponse(), result.getFailure());
			}
        }
    }

	protected class ProxyInputStreamContentProvider extends InputStreamContentProvider {
        private final HttpServletResponse response;
        private final Request proxyRequest;
        private final HttpServletRequest request;

		protected ProxyInputStreamContentProvider(HttpServletRequest request, HttpServletResponse response,
				Request proxyRequest, InputStream input) {
            super(input);
            this.request = request;
            this.response = response;
            this.proxyRequest = proxyRequest;
        }

        @Override
		public long getLength() {
            return request.getContentLength();
        }

        @Override
		protected ByteBuffer onRead(byte[] buffer, int offset, int length) {
            return onRequestContent(request, proxyRequest, buffer, offset, length);
        }

		protected ByteBuffer onRequestContent(HttpServletRequest request, Request proxyRequest, byte[] buffer,
				int offset, int length) {
            return super.onRead(buffer, offset, length);
        }

        @Override
		protected void onReadFailure(Throwable failure) {
            onClientRequestFailure(request, proxyRequest, response, failure);
        }
    }
}
