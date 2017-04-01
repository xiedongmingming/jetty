package extral.org.eclipse.jetty.servlets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;

/**
 * This is an extension to {@link DoSFilter} that uses Jetty APIs to
 * abruptly close the connection when the request times out.
 */

public class CloseableDoSFilter extends DoSFilter {
    @Override
	protected void onRequestTimeout(HttpServletRequest request, HttpServletResponse response, Thread handlingThread) {
        Request base_request=Request.getBaseRequest(request);
        base_request.getHttpChannel().getEndPoint().close();
    }
}
