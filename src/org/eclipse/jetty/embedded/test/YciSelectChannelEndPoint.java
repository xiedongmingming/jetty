package org.eclipse.jetty.embedded.test;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.SelectChannelEndPoint;
import org.eclipse.jetty.util.thread.Scheduler;

public class YciSelectChannelEndPoint extends SelectChannelEndPoint {

    private volatile long lastSelected = -1;

    public YciSelectChannelEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey key,
            Scheduler scheduler, long idleTimeout) {
        super(channel, selector, key, scheduler, idleTimeout);
    }

    @Override
	public Runnable onSelected() {
        lastSelected = System.currentTimeMillis();
		return super.onSelected();
    }

    public long getLastSelected() {
        return lastSelected;
    }
}
