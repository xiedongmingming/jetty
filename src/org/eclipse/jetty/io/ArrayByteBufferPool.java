package org.eclipse.jetty.io;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;

public class ArrayByteBufferPool implements ByteBufferPool {

    private final int _min;
    private final int _maxQueue;
    private final ByteBufferPool.Bucket[] _direct;
    private final ByteBufferPool.Bucket[] _indirect;
    private final int _inc;

    public ArrayByteBufferPool()    {
        this(-1,-1,-1,-1);
    }

    public ArrayByteBufferPool(int minSize, int increment, int maxSize)    {
        this(minSize,increment,maxSize,-1);
    }
    
    public ArrayByteBufferPool(int minSize, int increment, int maxSize, int maxQueue)    {
        if (minSize<=0){
        	minSize=0;
        }
		if (increment <= 0) {
        	increment=1024;
        }
		if (maxSize <= 0) {
			maxSize = 64 * 1024;
		}
		if (minSize >= increment) {
			throw new IllegalArgumentException("minSize >= increment");
		}
		if ((maxSize % increment) != 0 || increment >= maxSize) {
			throw new IllegalArgumentException("increment must be a divisor of maxSize");
		}
        _min=minSize;
        _inc=increment;

        _direct=new ByteBufferPool.Bucket[maxSize/increment];
        _indirect=new ByteBufferPool.Bucket[maxSize/increment];
        _maxQueue=maxQueue;

        int size=0;
		for (int i = 0; i < _direct.length; i++) {
            size+=_inc;
            _direct[i]=new ByteBufferPool.Bucket(size,_maxQueue);
            _indirect[i]=new ByteBufferPool.Bucket(size,_maxQueue);
        }
    }

    @Override
	public ByteBuffer acquire(int size, boolean direct) {
        ByteBufferPool.Bucket bucket = bucketFor(size,direct);
		if (bucket == null) {
			return direct ? BufferUtil.allocateDirect(size) : BufferUtil.allocate(size);
		}
        return bucket.acquire(direct);
            
    }

    @Override
	public void release(ByteBuffer buffer) {
		if (buffer != null) {
            ByteBufferPool.Bucket bucket = bucketFor(buffer.capacity(),buffer.isDirect());
			if (bucket != null) {
				bucket.release(buffer);
			}
        }
    }

	public void clear() {
		for (int i = 0; i < _direct.length; i++) {
            _direct[i].clear();
            _indirect[i].clear();
        }
    }

	private ByteBufferPool.Bucket bucketFor(int size, boolean direct) {
		if (size <= _min) {
			return null;
		}
        int b=(size-1)/_inc;
		if (b >= _direct.length) {
			return null;
		}
        ByteBufferPool.Bucket bucket = direct?_direct[b]:_indirect[b];
        return bucket;
    }

    // Package local for testing
	ByteBufferPool.Bucket[] bucketsFor(boolean direct) {
        return direct ? _direct : _indirect;
    }
}