package org.eclipse.jetty.server;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.MultiPartInputStreamParser;

public class MultiPartCleanerListener implements ServletRequestListener {
    public final static MultiPartCleanerListener INSTANCE = new MultiPartCleanerListener();
    
	protected MultiPartCleanerListener() {
    }
    
    @Override
	public void requestDestroyed(ServletRequestEvent sre) {
        //Clean up any tmp files created by MultiPartInputStream
        MultiPartInputStreamParser mpis = (MultiPartInputStreamParser)sre.getServletRequest().getAttribute(Request.__MULTIPART_INPUT_STREAM);
		if (mpis != null) {
            ContextHandler.Context context = (ContextHandler.Context)sre.getServletRequest().getAttribute(Request.__MULTIPART_CONTEXT);

            //Only do the cleanup if we are exiting from the context in which a servlet parsed the multipart files
			if (context == sre.getServletContext()) {
				try {
                    mpis.deleteParts();
				} catch (MultiException e) {
                    sre.getServletContext().log("Errors deleting multipart tmp files", e);
                }
            }
        }
    }

    @Override
	public void requestInitialized(ServletRequestEvent sre) {
        //nothing to do, multipart config set up by ServletHolder.handle()
    }
    
}