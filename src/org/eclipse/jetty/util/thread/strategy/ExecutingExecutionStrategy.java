package org.eclipse.jetty.util.thread.strategy;

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;

public abstract class ExecutingExecutionStrategy implements ExecutionStrategy {

    private static final Logger LOG = Log.getLogger(ExecutingExecutionStrategy.class);

	private final Executor _executor;// 提供默认的执行器

	// ******************************************************************
	protected ExecutingExecutionStrategy(Executor executor) {
		_executor = executor;
    }
	// ******************************************************************

	protected boolean execute(Runnable task) {

		try {

			_executor.execute(task);//

            return true;

		} catch (RejectedExecutionException e) {

			LOG.debug(e);
			LOG.warn("rejected execution of {}", task);

			try {

				if (task instanceof Closeable) {
					((Closeable) task).close();
				}

			} catch (Exception x) {

                e.addSuppressed(x);

                LOG.warn(e);
            }

        }
        return false;
    }
}
