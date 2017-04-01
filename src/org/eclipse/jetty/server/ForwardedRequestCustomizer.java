package org.eclipse.jetty.server;

import java.net.InetSocketAddress;

import org.eclipse.jetty.http.HostPortHttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.server.HttpConfiguration.Customizer;

public class ForwardedRequestCustomizer implements Customizer {
    private HostPortHttpField _hostHeader;
    private String _forwardedHostHeader = HttpHeader.X_FORWARDED_HOST.toString();
    private String _forwardedServerHeader = HttpHeader.X_FORWARDED_SERVER.toString();
    private String _forwardedForHeader = HttpHeader.X_FORWARDED_FOR.toString();
    private String _forwardedProtoHeader = HttpHeader.X_FORWARDED_PROTO.toString();
    private String _forwardedCipherSuiteHeader;
    private String _forwardedSslSessionIdHeader;
    
	public String getHostHeader() {
        return _hostHeader.getValue();
    }

	public void setHostHeader(String hostHeader) {
        _hostHeader = new HostPortHttpField(hostHeader);
    }

	public String getForwardedHostHeader() {
        return _forwardedHostHeader;
    }

	public void setForwardedHostHeader(String forwardedHostHeader) {
        _forwardedHostHeader = forwardedHostHeader;
    }

	public String getForwardedServerHeader() {
        return _forwardedServerHeader;
    }

	public void setForwardedServerHeader(String forwardedServerHeader) {
        _forwardedServerHeader = forwardedServerHeader;
    }

	public String getForwardedForHeader() {
        return _forwardedForHeader;
    }

	public void setForwardedForHeader(String forwardedRemoteAddressHeader) {
        _forwardedForHeader = forwardedRemoteAddressHeader;
    }

	public String getForwardedProtoHeader() {
        return _forwardedProtoHeader;
    }

	public void setForwardedProtoHeader(String forwardedProtoHeader) {
        _forwardedProtoHeader = forwardedProtoHeader;
    }

	public String getForwardedCipherSuiteHeader() {
        return _forwardedCipherSuiteHeader;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param forwardedCipherSuite
     *            The header name holding a forwarded cipher suite (default null)
     */
    public void setForwardedCipherSuiteHeader(String forwardedCipherSuite)
    {
        _forwardedCipherSuiteHeader = forwardedCipherSuite;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The header name holding a forwarded SSL Session ID (default null)
     */
    public String getForwardedSslSessionIdHeader()
    {
        return _forwardedSslSessionIdHeader;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param forwardedSslSessionId
     *            The header name holding a forwarded SSL Session ID (default null)
     */
    public void setForwardedSslSessionIdHeader(String forwardedSslSessionId)
    {
        _forwardedSslSessionIdHeader = forwardedSslSessionId;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void customize(Connector connector, HttpConfiguration config, Request request)
    {
        HttpFields httpFields = request.getHttpFields();

        // Do SSL first
        if (getForwardedCipherSuiteHeader()!=null)
        {
            String cipher_suite=httpFields.get(getForwardedCipherSuiteHeader());
            if (cipher_suite!=null)
                request.setAttribute("javax.servlet.request.cipher_suite",cipher_suite);
        }
        if (getForwardedSslSessionIdHeader()!=null)
        {
            String ssl_session_id=httpFields.get(getForwardedSslSessionIdHeader());
            if(ssl_session_id!=null)
            {
                request.setAttribute("javax.servlet.request.ssl_session_id", ssl_session_id);
                request.setScheme(HttpScheme.HTTPS.asString());
            }
        }

        // Retrieving headers from the request
        String forwardedHost = getLeftMostFieldValue(httpFields,getForwardedHostHeader());
        String forwardedServer = getLeftMostFieldValue(httpFields,getForwardedServerHeader());
        String forwardedFor = getLeftMostFieldValue(httpFields,getForwardedForHeader());
        String forwardedProto = getLeftMostFieldValue(httpFields,getForwardedProtoHeader());

        if (_hostHeader != null)
        {
            // Update host header
            httpFields.put(_hostHeader);
            request.setAuthority(_hostHeader.getHost(),_hostHeader.getPort());
        }
        else if (forwardedHost != null)
        {
            // Update host header
            HostPortHttpField auth = new HostPortHttpField(forwardedHost);
            httpFields.put(auth);
            request.setAuthority(auth.getHost(),auth.getPort());
        }
        else if (forwardedServer != null)
        {
            // Use provided server name
            request.setAuthority(forwardedServer,request.getServerPort());
        }

        if (forwardedFor != null)
        {
            request.setRemoteAddr(InetSocketAddress.createUnresolved(forwardedFor,request.getRemotePort()));
        }

        if (forwardedProto != null)
        {
            request.setScheme(forwardedProto);
            if (forwardedProto.equals(config.getSecureScheme()))
                request.setSecure(true);
        }
    }

	protected String getLeftMostFieldValue(HttpFields fields, String header) {
		if (header == null) {
			return null;
		}
        String headerValue = fields.get(header);
		if (headerValue == null) {
			return null;
		}
        int commaIndex = headerValue.indexOf(',');
		if (commaIndex == -1) {
            // Single value
            return headerValue;
        }
        // The left-most value is the farthest downstream client
        return headerValue.substring(0,commaIndex);
    }
    @Override
	public String toString() {
        return String.format("%s@%x",this.getClass().getSimpleName(),hashCode());
    }
}
