package org.eclipse.jetty.util.thread;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class TimerScheduler extends AbstractLifeCycle implements Scheduler, Runnable {

    private static final Logger LOG = Log.getLogger(TimerScheduler.class);

	private final String _name;
    private final boolean _daemon;

	private Timer _timer;//

	// ********************************************************************************
	public TimerScheduler() {
        this(null, false);
    }
	public TimerScheduler(String name, boolean daemon) {
        _name = name;
        _daemon = daemon;
    }
	// ********************************************************************************

    @Override
	protected void doStart() throws Exception {
        _timer = _name == null ? new Timer() : new Timer(_name, _daemon);
		run();//
        super.doStart();
    }
    @Override
	protected void doStop() throws Exception {
        _timer.cancel();
        super.doStop();
        _timer = null;
    }
	// ********************************************************************************

    @Override
	public Task schedule(final Runnable task, final long delay, final TimeUnit units) {
        Timer timer = _timer;
		if (timer == null) {
			throw new RejectedExecutionException("STOPPED: " + this);
		}
        SimpleTask t = new SimpleTask(task);
        timer.schedule(t, units.toMillis(delay));
        return t;
    }

    @Override
	public void run() {
        Timer timer = _timer;
		if (timer != null) {
			timer.purge();//
            schedule(this, 1, TimeUnit.SECONDS);
        }
    }

	private static class SimpleTask extends TimerTask implements Task {
		private final Runnable _task;//
		private SimpleTask(Runnable runnable) {
            _task = runnable;
        }
        @Override
		public void run() {
			try {
				_task.run();
			} catch (Throwable x) {
                LOG.warn("Exception while executing task " + _task, x);
            }
        }
        @Override
		public String toString() {
			return String.format("%s.%s@%x", TimerScheduler.class.getSimpleName(), SimpleTask.class.getSimpleName(),
					hashCode());
        }
    }
}
