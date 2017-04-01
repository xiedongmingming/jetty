package extral.org.eclipse.jetty.client;

import extral.org.eclipse.jetty.client.api.Response;

@SuppressWarnings("serial")
public class HttpResponseException extends RuntimeException
{
    private final Response response;

    public HttpResponseException(String message, Response response)
    {
        super(message);
        this.response = response;
    }

    public Response getResponse()
    {
        return response;
    }
}
