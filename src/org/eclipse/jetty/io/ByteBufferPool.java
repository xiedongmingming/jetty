package org.eclipse.jetty.io;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.ConcurrentArrayQueue;

public interface ByteBufferPool {

    public ByteBuffer acquire(int size, boolean direct);

    public void release(ByteBuffer buffer);

	public static class Lease {

        private final ByteBufferPool byteBufferPool;
        private final List<ByteBuffer> buffers;
        private final List<Boolean> recycles;

		public Lease(ByteBufferPool byteBufferPool) {
            this.byteBufferPool = byteBufferPool;
            this.buffers = new ArrayList<>();
            this.recycles = new ArrayList<>();
        }

		public ByteBuffer acquire(int capacity, boolean direct) {
            ByteBuffer buffer = byteBufferPool.acquire(capacity, direct);
            BufferUtil.clearToFill(buffer);
            return buffer;
        }

		public void append(ByteBuffer buffer, boolean recycle) {
            buffers.add(buffer);
            recycles.add(recycle);
        }

		public void insert(int index, ByteBuffer buffer, boolean recycle) {
            buffers.add(index, buffer);
            recycles.add(index, recycle);
        }

		public List<ByteBuffer> getByteBuffers() {
            return buffers;
        }

		public long getTotalLength() {
            long length = 0;
			for (int i = 0; i < buffers.size(); ++i) {
				length += buffers.get(i).remaining();
			}
            return length;
        }

		public int getSize() {
            return buffers.size();
        }

		public void recycle() {
			for (int i = 0; i < buffers.size(); ++i) {
                ByteBuffer buffer = buffers.get(i);
				if (recycles.get(i)) {
					byteBufferPool.release(buffer);
				}
            }
            buffers.clear();
            recycles.clear();
        }
    }

	class Bucket {
        private final int _capacity;
        private final AtomicInteger _space;
        private final Queue<ByteBuffer> _queue= new ConcurrentArrayQueue<>();

		public Bucket(int bufferSize, int maxSize) {
			_capacity = bufferSize;
			_space = maxSize > 0 ? new AtomicInteger(maxSize) : null;
        }
    
		public void release(ByteBuffer buffer) {
            BufferUtil.clear(buffer);
			if (_space == null) {
				_queue.offer(buffer);
			} else if (_space.decrementAndGet() >= 0) {
				_queue.offer(buffer);
			} else {
				_space.incrementAndGet();
			}
        }

		public ByteBuffer acquire(boolean direct) {
            ByteBuffer buffer = _queue.poll();
			if (buffer == null) {
				return direct ? BufferUtil.allocateDirect(_capacity) : BufferUtil.allocate(_capacity);
			}
			if (_space != null) {
				_space.incrementAndGet();
			}
            return buffer;        
        }
    
		public void clear() {
			if (_space == null) {
				_queue.clear();
			} else {
                int s=_space.getAndSet(0);
				while (s-- > 0) {
					if (_queue.poll() == null) {
						_space.incrementAndGet();
					}
                }
            }
        }

		boolean isEmpty() {
            return _queue.isEmpty();
        }

		int size() {
            return _queue.size();
        }
        
        @Override
		public String toString() {
            return String.format("Bucket@%x{%d,%d}",hashCode(),_capacity,_queue.size());
        }
    }
}
