package org.eclipse.jetty.util.statistic;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

public class CounterStatistic {

	protected final LongAccumulator _max = new LongAccumulator(Math::max, 0L);
	protected final LongAdder _total = new LongAdder();

    protected final AtomicLong _current = new AtomicLong();

	public void reset() {
        _total.reset();
        _max.reset();
		long current = _current.get();
        _total.add(current);
        _max.accumulate(current);
    }
	public void reset(final long value) {
        _current.set(value);
        _total.reset();
        _max.reset();
		if (value > 0) {
            _total.add(value);
            _max.accumulate(value);
        }
    }
	public long add(final long delta) {
		long value = _current.addAndGet(delta);
		if (delta > 0) {
            _total.add(delta);
            _max.accumulate(value);
        }
        return value;
    }
	public long increment() {
		long value = _current.incrementAndGet();
        _total.increment();
        _max.accumulate(value);
        return value;
    }
	public long decrement() {
        return _current.decrementAndGet();
    }
	public long getMax() {
        return _max.get();
    }
	public long getCurrent() {
        return _current.get();
    }
	public long getTotal() {
        return _total.sum();
    }
    @Override
	public String toString() {
		return String.format("%s@%x{c=%d,m=%d,t=%d}", this.getClass().getSimpleName(), hashCode(), _current.get(),
				_max.get(), _total.sum());
    }
}
