package org.eclipse.jetty.util.statistic;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

public class SampleStatistic {

    protected final LongAccumulator _max = new LongAccumulator(Math::max,0L);
    protected final AtomicLong _total = new AtomicLong();
    protected final AtomicLong _count = new AtomicLong();
    protected final LongAdder _totalVariance100 = new LongAdder();

	public void reset() {
        _max.reset();
        _total.set(0);
        _count.set(0);
        _totalVariance100.reset();
    }
	public void set(final long sample) {
        long total = _total.addAndGet(sample);
        long count = _count.incrementAndGet();
		if (count > 1) {
			long mean10 = total * 10 / count;
			long delta10 = sample * 10 - mean10;
			_totalVariance100.add(delta10 * delta10);
        }
        _max.accumulate(sample);
    }
	public long getMax() {
        return _max.get();
    }
	public long getTotal() {
        return _total.get();
    }
	public long getCount() {
        return _count.get();
    }
	public double getMean() {
        return (double)_total.get()/_count.get();
    }
	public double getVariance() {
        final long variance100 = _totalVariance100.sum();
        final long count = _count.get();
		return count > 1 ? (variance100) / 100.0 / (count - 1) : 0.0;
    }
	public double getStdDev() {
        return Math.sqrt(getVariance());
    }
    @Override
	public String toString() {
		return String.format("%s@%x{c=%d,m=%d,t=%d,v100=%d}", this.getClass().getSimpleName(), hashCode(), _count.get(),
				_max.get(), _total.get(), _totalVariance100.sum());
    }
}
