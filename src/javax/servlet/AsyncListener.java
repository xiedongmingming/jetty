package javax.servlet;

import java.io.IOException;
import java.util.EventListener;

public interface AsyncListener extends EventListener {

    public void onComplete(AsyncEvent event) throws IOException;
    public void onTimeout(AsyncEvent event) throws IOException;
    public void onError(AsyncEvent event) throws IOException;
	public void onStartAsync(AsyncEvent event) throws IOException;

}
