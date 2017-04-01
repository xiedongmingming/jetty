package org.eclipse.jetty.server;

import java.io.IOException;

import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.log.Slf4jLog;

@ManagedObject("NCSA standard format request log to slf4j bridge")
public class Slf4jRequestLog extends AbstractNCSARequestLog {
    private Slf4jLog logger;
    private String loggerName;

	public Slf4jRequestLog() {
        this.loggerName = "org.eclipse.jetty.server.RequestLog";
    }

	public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

	public String getLoggerName() {
        return loggerName;
    }

    @Override
	protected boolean isEnabled() {
        return logger != null;
    }

    @Override
	public void write(String requestEntry) throws IOException {
        logger.info(requestEntry);
    }

    @Override
	protected synchronized void doStart() throws Exception {
        logger = new Slf4jLog(loggerName);
        super.doStart();
    }
}
