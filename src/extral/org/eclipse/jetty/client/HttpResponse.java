package extral.org.eclipse.jetty.client;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpVersion;

import extral.org.eclipse.jetty.client.api.Request;
import extral.org.eclipse.jetty.client.api.Response;

public class HttpResponse implements Response
{
    private final HttpFields headers = new HttpFields();
    private final Request request;
    private final List<ResponseListener> listeners;
    private HttpVersion version;
    private int status;
    private String reason;

    public HttpResponse(Request request, List<ResponseListener> listeners)
    {
        this.request = request;
        this.listeners = listeners;
    }

    @Override
    public Request getRequest()
    {
        return request;
    }

    @Override
	public HttpVersion getVersion()
    {
        return version;
    }

    public HttpResponse version(HttpVersion version)
    {
        this.version = version;
        return this;
    }

    @Override
    public int getStatus()
    {
        return status;
    }

    public HttpResponse status(int status)
    {
        this.status = status;
        return this;
    }

    @Override
	public String getReason()
    {
        return reason;
    }

    public HttpResponse reason(String reason)
    {
        this.reason = reason;
        return this;
    }

    @Override
    public HttpFields getHeaders()
    {
        return headers;
    }

	@SuppressWarnings("unchecked")
	@Override
    public <T extends ResponseListener> List<T> getListeners(Class<T> type)
    {
        ArrayList<T> result = new ArrayList<>();
        for (ResponseListener listener : listeners)
            if (type == null || type.isInstance(listener))
                result.add((T)listener);
        return result;
    }

    @Override
    public boolean abort(Throwable cause)
    {
        return request.abort(cause);
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s %d %s]@%x", HttpResponse.class.getSimpleName(), getVersion(), getStatus(), getReason(), hashCode());
    }
}
