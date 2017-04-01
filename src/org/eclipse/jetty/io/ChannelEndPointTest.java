package org.eclipse.jetty.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.eclipse.jetty.util.thread.Scheduler;

public class ChannelEndPointTest extends AbstractEndPoint {

	protected ChannelEndPointTest(Scheduler scheduler, InetSocketAddress local, InetSocketAddress remote) {
		super(scheduler, local, remote);
	}

	@Override
	public void shutdownOutput() {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isOutputShutdown() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isInputShutdown() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int fill(ByteBuffer buffer) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean flush(ByteBuffer... buffer) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getTransport() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void onIncompleteFlush() {
		// TODO Auto-generated method stub
	}

	@Override
	protected void needsFillInterest() throws IOException {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isOpen() {
		// TODO Auto-generated method stub
		return false;
	}

}
