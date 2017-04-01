package extral.org.eclipse.jetty.client;

import java.net.URI;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;

import extral.org.eclipse.jetty.client.api.Request;
import extral.org.eclipse.jetty.client.api.Response;

/**
 * <p>A protocol handler that handles the 401 response code
 * in association with the {@code WWW-Authenticate} header.</p>
 *
 * @see ProxyAuthenticationProtocolHandler
 */
public class WWWAuthenticationProtocolHandler extends AuthenticationProtocolHandler {
    public static final String NAME = "www-authenticate";
    private static final String ATTRIBUTE = WWWAuthenticationProtocolHandler.class.getName() + ".attribute";

	public WWWAuthenticationProtocolHandler(HttpClient client) {
        this(client, DEFAULT_MAX_CONTENT_LENGTH);
    }

	public WWWAuthenticationProtocolHandler(HttpClient client, int maxContentLength) {
        super(client, maxContentLength);
    }

    @Override
	public String getName() {
        return NAME;
    }

    @Override
	public boolean accept(Request request, Response response) {
        return response.getStatus() == HttpStatus.UNAUTHORIZED_401;
    }

    @Override
	protected HttpHeader getAuthenticateHeader() {
        return HttpHeader.WWW_AUTHENTICATE;
    }

    @Override
	protected HttpHeader getAuthorizationHeader() {
        return HttpHeader.AUTHORIZATION;
    }

	protected URI getAuthenticationURI(Request request) {
        return request.getURI();
    }

    @Override
	protected String getAuthenticationAttribute() {
        return ATTRIBUTE;
    }
}
