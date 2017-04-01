package org.eclipse.jetty.util.thread.strategy;

import java.util.concurrent.Executor;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.Locker;

public class ProduceConsume implements ExecutionStrategy, Runnable {

    private static final Logger LOG = Log.getLogger(ExecuteProduceConsume.class);

    private final Locker _locker = new Locker();

	private final Producer _producer;// 生产者
	private final Executor _executor;// 消费者

    private State _state = State.IDLE;

	public ProduceConsume(Producer producer, Executor executor) {
		System.out.println("生产者消费者分别是谁: " + producer.getClass().getName() + ":" + executor.getClass().getName());
        this._producer = producer;
        this._executor = executor;
    }

	// ****************************************************************************
    @Override
	public void execute() {

		try (Locker.Lock lock = _locker.lock()) {

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

			Runnable task = _producer.produce();// 生产一个任务

			if (LOG.isDebugEnabled()) {
				LOG.debug("{} produced {}", _producer, task);
			}

			if (task == null) {
				try (Locker.Lock lock = _locker.lock()) {
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
			task.run(); // 执行该任务
        }
    }
    @Override
	public void dispatch() {
        _executor.execute(this);
    }
	// ****************************************************************************
    @Override
	public void run() {
        execute();
    }
	// ****************************************************************************
	public static class Factory implements ExecutionStrategy.Factory {
        @Override
		public ExecutionStrategy newExecutionStrategy(Producer producer, Executor executor) {
            return new ProduceConsume(producer, executor);
        }
    }
	private enum State {
        IDLE, PRODUCE, EXECUTE
    }
}
