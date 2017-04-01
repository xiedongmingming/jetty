package javax.servlet.http;

import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;

/**
 * This interface encapsulates the connection for an upgrade request.
 * It allows the protocol handler to send service requests and status
 * queries to the container.
 *
 * @since Servlet 3.1
 */

public interface WebConnection extends AutoCloseable {
    public ServletInputStream getInputStream() throws IOException;
    public ServletOutputStream getOutputStream() throws IOException;
}
