package org.eclipse.jetty.io;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.util.thread.Scheduler;

public class SelectChannelEndPointTest extends SelectChannelEndPoint {

	public SelectChannelEndPointTest(SocketChannel channel, ManagedSelector selector, SelectionKey key, Scheduler scheduler, long idleTimeout) {
		super(channel, selector, key, scheduler, idleTimeout);
	}

}
