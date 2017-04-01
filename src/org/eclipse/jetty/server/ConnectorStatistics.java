package org.eclipse.jetty.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.statistic.CounterStatistic;
import org.eclipse.jetty.util.statistic.SampleStatistic;

/** A Connector.Listener that gathers Connector and Connections Statistics.
 * Adding an instance of this class as with {@link AbstractConnector#addBean(Object)} 
 * will register the listener with all connections accepted by that connector.
 */
@ManagedObject("Connector Statistics")
public class ConnectorStatistics extends AbstractLifeCycle implements Dumpable, Connection.Listener {

	private final static Sample ZERO = new Sample();

    private final AtomicLong _startMillis = new AtomicLong(-1L);

    private final CounterStatistic _connectionStats = new CounterStatistic();

    private final SampleStatistic _messagesIn = new SampleStatistic();
    private final SampleStatistic _messagesOut = new SampleStatistic();
    private final SampleStatistic _connectionDurationStats = new SampleStatistic();

    private final ConcurrentMap<Connection, Sample> _samples = new ConcurrentHashMap<>();

    private final LongAdder _closedIn = new LongAdder();
    private final LongAdder _closedOut = new LongAdder();

    private AtomicLong _nanoStamp=new AtomicLong();

    private volatile int _messagesInPerSecond;
    private volatile int _messagesOutPerSecond;

    @Override
	public void onOpened(Connection connection) {
		if (isStarted()) {
            _connectionStats.increment();
            _samples.put(connection,ZERO);
        }
    }
    @Override
	public void onClosed(Connection connection) {
		if (isStarted()) {
			int msgsIn = connection.getMessagesIn();
			int msgsOut = connection.getMessagesOut();
            _messagesIn.set(msgsIn);
            _messagesOut.set(msgsOut);
            _connectionStats.decrement();
            _connectionDurationStats.set(System.currentTimeMillis()-connection.getCreatedTimeStamp());
			Sample sample = _samples.remove(connection);
			if (sample != null) {
                _closedIn.add(msgsIn-sample._messagesIn);
                _closedOut.add(msgsOut-sample._messagesOut);
            }
        }
    }
	public int getBytesIn() {
        return -1;
    }
	public int getBytesOut() {
        return -1;
    }
	public int getConnections() {
        return (int)_connectionStats.getTotal();
    }
	public long getConnectionDurationMax() {
        return _connectionDurationStats.getMax();
    }
	public double getConnectionDurationMean() {
        return _connectionDurationStats.getMean();
    }
	public double getConnectionDurationStdDev() {
        return _connectionDurationStats.getStdDev();
    }
	public int getMessagesIn() {
        return (int)_messagesIn.getTotal();
    }
	public int getMessagesInPerConnectionMax() {
        return (int)_messagesIn.getMax();
    }
	public double getMessagesInPerConnectionMean() {
        return _messagesIn.getMean();
    }
	public double getMessagesInPerConnectionStdDev() {
        return _messagesIn.getStdDev();
    }
	public int getConnectionsOpen() {
        return (int)_connectionStats.getCurrent();
    }
	public int getConnectionsOpenMax() {
        return (int)_connectionStats.getMax();
    }
	public int getMessagesOut() {
        return (int)_messagesIn.getTotal();
    }
	public int getMessagesOutPerConnectionMax() {
        return (int)_messagesIn.getMax();
    }
	public double getMessagesOutPerConnectionMean() {
        return _messagesIn.getMean();
    }
	public double getMessagesOutPerConnectionStdDev() {
        return _messagesIn.getStdDev();
    }
	public long getStartedMillis() {
        long start = _startMillis.get();
        return start < 0 ? 0 : System.currentTimeMillis() - start;
    }
	public int getMessagesInPerSecond() {
        update();
        return _messagesInPerSecond;
    }
	public int getMessagesOutPerSecond() {
        update();
        return _messagesOutPerSecond;
    }

    @Override
	public void doStart() {
        reset();
    }

    @Override
	public void doStop() {
        _samples.clear();
    }
	public void reset() {
        _startMillis.set(System.currentTimeMillis());
        _messagesIn.reset();
        _messagesOut.reset();
        _connectionStats.reset();
        _connectionDurationStats.reset();
        _samples.clear();
    }

    @Override
	public String dump() {
        return ContainerLifeCycle.dump(this);
    }

    @Override
	public void dump(Appendable out, String indent) throws IOException {
        ContainerLifeCycle.dumpObject(out,this);
        ContainerLifeCycle.dump(out,indent,Arrays.asList(new String[]{"connections="+_connectionStats,"duration="+_connectionDurationStats,"in="+_messagesIn,"out="+_messagesOut}));
    }
    
	public static void addToAllConnectors(Server server) {
		for (Connector connector : server.getConnectors()) {
			if (connector instanceof Container) {
				((Container) connector).addBean(new ConnectorStatistics());
			}
        }
    }  
    
    private static final long SECOND_NANOS=TimeUnit.SECONDS.toNanos(1);

	private synchronized void update() {
		long now = System.nanoTime();
		long then = _nanoStamp.get();
		long duration = now - then;
		if (duration > SECOND_NANOS / 2) {
			if (_nanoStamp.compareAndSet(then, now)) {
				long msgsIn = _closedIn.sumThenReset();
				long msgsOut = _closedOut.sumThenReset();
				for (Map.Entry<Connection, Sample> entry : _samples.entrySet()) {
					Connection connection = entry.getKey();
                    Sample sample = entry.getValue();
                    Sample next = new Sample(connection);
					if (_samples.replace(connection, sample, next)) {
						msgsIn += next._messagesIn - sample._messagesIn;
						msgsOut += next._messagesOut - sample._messagesOut;
                    }
                }
				_messagesInPerSecond = (int) (msgsIn * SECOND_NANOS / duration);
				_messagesOutPerSecond = (int) (msgsOut * SECOND_NANOS / duration);
            }
        }
    }
    
	private static class Sample {
		Sample() {
			_messagesIn = 0;
			_messagesOut = 0;
        }
		Sample(Connection connection) {
			_messagesIn = connection.getMessagesIn();
			_messagesOut = connection.getMessagesOut();
        }
        final int _messagesIn;
        final int _messagesOut;
    }
}
