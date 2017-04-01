package org.eclipse.jetty.server;

import java.util.Objects;

public class HostHeaderCustomizer implements HttpConfiguration.Customizer
{
    private final String serverName;
    private final int serverPort;

    /**
     * @param serverName the {@code serverName} to set on the request (the {@code serverPort} will not be set)
     */
    public HostHeaderCustomizer(String serverName)
    {
        this(serverName, 0);
    }

    /**
     * @param serverName the {@code serverName} to set on the request
     * @param serverPort the {@code serverPort} to set on the request
     */
    public HostHeaderCustomizer(String serverName, int serverPort)
    {
        this.serverName = Objects.requireNonNull(serverName);
        this.serverPort = serverPort;
    }

    @Override
    public void customize(Connector connector, HttpConfiguration channelConfig, Request request)
    {
        if (request.getHeader("Host") == null)
            request.setAuthority(serverName,serverPort);  // TODO set the field as well?
    }
}
