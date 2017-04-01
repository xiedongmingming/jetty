package org.eclipse.jetty.util.thread.strategy;

import java.util.concurrent.Executor;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.Locker;
import org.eclipse.jetty.util.thread.Locker.Lock;

public class ProduceExecuteConsume extends ExecutingExecutionStrategy implements ExecutionStrategy {

    private static final Logger LOG = Log.getLogger(ProduceExecuteConsume.class);

    private final Locker _locker = new Locker();

    private final Producer _producer;

    private State _state = State.IDLE;

	public ProduceExecuteConsume(Producer producer, Executor executor) {
        super(executor);
        this._producer = producer;
    }

	// ****************************************************************************
    @Override
	public void execute() {
		try (Lock locked = _locker.lock()) {
			switch (_state) {
                case IDLE:
                	_state = State.PRODUCE;
                    break;
                case PRODUCE:
                case EXECUTE:
                    _state = State.EXECUTE;
                    return;
            }
        }
		while (true) {
			
			Runnable task = _producer.produce();

			if (LOG.isDebugEnabled()) {
				LOG.debug("{} produced {}", _producer, task);
			}
			
			if (task == null) {
				
				try (Lock locked = _locker.lock()) {
					switch (_state) {
                        case IDLE:
                            throw new IllegalStateException();
                        case PRODUCE:
                            _state = State.IDLE;
                            return;
                        case EXECUTE:
                            _state = State.PRODUCE;
                            continue;
                    }
                }
            }
			execute(task);
        }        
    }
    @Override
	public void dispatch() {
        execute();
    }
	// ****************************************************************************

	public static class Factory implements ExecutionStrategy.Factory {
        @Override
		public ExecutionStrategy newExecutionStrategy(Producer producer, Executor executor) {
            return new ProduceExecuteConsume(producer, executor);
        }
    }
	private enum State {
        IDLE, PRODUCE, EXECUTE
    }
}
