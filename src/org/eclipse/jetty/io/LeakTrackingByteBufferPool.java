package org.eclipse.jetty.io;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.LeakDetector;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class LeakTrackingByteBufferPool extends ContainerLifeCycle implements ByteBufferPool {

    private static final Logger LOG = Log.getLogger(LeakTrackingByteBufferPool.class);

	private final LeakDetector<ByteBuffer> leakDetector = new LeakDetector<ByteBuffer>() {
		public String id(ByteBuffer resource) {
            return BufferUtil.toIDString(resource);
        }
        @Override
		protected void leaked(LeakInfo leakInfo) {
            leaked.incrementAndGet();
            LeakTrackingByteBufferPool.this.leaked(leakInfo);
        }
    };

    private final static boolean NOISY = Boolean.getBoolean(LeakTrackingByteBufferPool.class.getName() + ".NOISY");
    private final ByteBufferPool delegate;
    private final AtomicLong leakedReleases = new AtomicLong(0);
    private final AtomicLong leakedAcquires = new AtomicLong(0);
    private final AtomicLong leaked = new AtomicLong(0);

	public LeakTrackingByteBufferPool(ByteBufferPool delegate) {
        this.delegate = delegate;
        addBean(leakDetector);
        addBean(delegate);
    }

    @Override
	public ByteBuffer acquire(int size, boolean direct) {
        ByteBuffer buffer = delegate.acquire(size, direct);
        boolean leaked = leakDetector.acquired(buffer);
		if (NOISY || !leaked) {
            leakedAcquires.incrementAndGet();
            LOG.info(String.format("ByteBuffer acquire %s leaked.acquired=%s", leakDetector.id(buffer), leaked ? "normal" : "LEAK"),
                    new Throwable("LeakStack.Acquire"));
        }
        return buffer;
    }

    @Override
	public void release(ByteBuffer buffer) {
		if (buffer == null) {
			return;
		}
        boolean leaked = leakDetector.released(buffer);
		if (NOISY || !leaked) {
            leakedReleases.incrementAndGet();
            LOG.info(String.format("ByteBuffer release %s leaked.released=%s", leakDetector.id(buffer), leaked ? "normal" : "LEAK"), new Throwable(
                    "LeakStack.Release"));
        }
        delegate.release(buffer);
    }
	public void clearTracking() {
        leakedAcquires.set(0);
        leakedReleases.set(0);
    }
	public long getLeakedAcquires() {
        return leakedAcquires.get();
    }
	public long getLeakedReleases() {
        return leakedReleases.get();
    }
	public long getLeakedResources() {
        return leaked.get();
    }
	protected void leaked(LeakDetector<ByteBuffer>.LeakInfo leakInfo) {
        LOG.warn("ByteBuffer " + leakInfo.getResourceDescription() + " leaked at:", leakInfo.getStackFrames());
    }
}
