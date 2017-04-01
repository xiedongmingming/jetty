package org.eclipse.jetty.util.thread.strategy;

import java.io.Closeable;
import java.util.concurrent.Executor;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.Locker;
import org.eclipse.jetty.util.thread.Locker.Lock;
import org.eclipse.jetty.util.thread.ThreadPool;

//为每个SELECTOR创建的执行结构
public class ExecuteProduceConsume extends ExecutingExecutionStrategy implements ExecutionStrategy, Runnable {// 作为一个JOB由线程来运行
    
	private static final Logger LOG = Log.getLogger(ExecuteProduceConsume.class);

    private final Locker _locker = new Locker();

    private final Runnable _runExecute = new RunExecute();
	private final Producer _producer;// SelectorProducer

	private final ThreadPool _threadPool;// QueuedThreadPool

	private boolean _idle = true;// 表示当前空闲(可以生产)否则可以执行
	private boolean _producing;// 表示正在生产
	private boolean _execute;// 表示正在执行

    private boolean _pending;
    private boolean _lowThreads;

	// ******************************************************************
    public ExecuteProduceConsume(Producer producer, Executor executor) {

		super(executor);

		// 生产执行器:
		// org.eclipse.jetty.io.ManagedSelector$SelectorProducer
		// org.eclipse.jetty.util.thread.QueuedThreadPool

		System.out.println("生产器和执行器分别为: " + producer.getClass().getName() + " : " + executor.getClass().getName());

        this._producer = producer;

        _threadPool = executor instanceof ThreadPool ? (ThreadPool)executor : null;
    }
    @Deprecated
	public ExecuteProduceConsume(Producer producer, Executor executor, ExecutionStrategy lowResourceStrategy) {
        this(producer, executor);
    }

	// ******************************************************************
	// 接口实现方法
    @Override
	public void execute() {// 执行体--ManagedSelector

        if (LOG.isDebugEnabled()) {
			LOG.debug("{} execute", this);
		}

        boolean produce = false;

		try (Lock locked = _locker.lock()) {// 进行状态的变换
			if (_idle) {
				if (_producing) {// 表示线程空闲时不可能处于正在生产的过程中
					throw new IllegalStateException();
				}
				produce = _producing = true;// 表示可以生产了
                _idle = false;
            } else {
                _execute = true;
            }
        }
		if (produce) {// 表示可以生产
			produceConsume();
		}
    }
    @Override
    public void dispatch() {

		if (LOG.isDebugEnabled()) {
			LOG.debug("{} spawning", this);
		}

        boolean dispatch = false;

        try (Lock locked = _locker.lock()) {
			if (_idle) {
				dispatch = true;
			} else {
				_execute = true;
			}
        }
		if (dispatch) {
			execute(_runExecute);
		}
    }
	// ******************************************************************

    @Override
    public void run() {

		if (LOG.isDebugEnabled()) {
			LOG.debug("{} run", this);
		}

        boolean produce = false;

        try (Lock locked = _locker.lock()) {
            _pending = false;
            if (!_idle && !_producing) {
                produce = _producing = true;
            }
        }

		if (produce) {
			produceConsume();
		}
    }

	private void produceConsume() {// 处理

		if (_threadPool != null && _threadPool.isLowOnThreads()) {// 表示线程不够用啦

			// *********************************************************
			// if we are low on threads we must not produce and consume
            // in the same thread, but produce and execute to consume.
			// *********************************************************

			if (!produceExecuteConsume()) {
				return;
			}
        }

        executeProduceConsume();
    }
	public boolean isLowOnThreads() {
        return _lowThreads;
    }
	private boolean produceExecuteConsume() {// 只有一个地方会调用

		if (LOG.isDebugEnabled()) {
			LOG.debug("{} enter low threads mode", this);
		}

        _lowThreads = true;

        try {

            boolean idle = false;

            while (_threadPool.isLowOnThreads()) {

				Runnable task = _producer.produce();//

				if (LOG.isDebugEnabled()) {
					LOG.debug("{} produced {}", _producer, task);
				}

                if (task == null) {
					try (Lock locked = _locker.lock()) {
                        if (_execute) {
                            _execute = false;
                            _producing = true;
                            _idle = false;
                            continue;
                        }

                        _producing = false;
                        idle = _idle = true;
                        break;
                    }
                }

				System.out.println("产生的任务是什么: " + task.getClass().getName());

				executeProduct(task);
            }

            return !idle;

        } finally {

            _lowThreads = false;

			if (LOG.isDebugEnabled()) {
				LOG.debug("{} exit low threads mode", this);
			}
        }
    }
    protected void executeProduct(Runnable task) {
        if (task instanceof Rejectable) {
            try {
                ((Rejectable)task).reject();
				if (task instanceof Closeable) {
					((Closeable) task).close();
				}
            } catch (Throwable x) {
                LOG.debug(x);
            }
        } else {
            execute(task);
        }
    }

	private void executeProduceConsume() {// 针对每个SELECTOR的底层循环监听--只有一个地方会调用--表示线程够用的情况下

		while (true) {
			Runnable task = _producer.produce();// 生成一个事件:ManagedSelector.SelectorProducer

			boolean dispatch = false;// 表示是否需要派送

			try (Lock locked = _locker.lock()) {

				_producing = false;// 表示停止生产

				if (task == null) {
                    if (_execute) {
                        _idle = false;
                        _producing = true;
                        _execute = false;
                        continue;
                    }
                    _idle = true;
                    break;
                }

				if (!_pending) {
                    dispatch = _pending = true;
                }
                _execute = false;

				// 产生的任务是什么: org.eclipse.jetty.io.ManagedSelector$CreateEndPoint
				// 产生的任务是什么: org.eclipse.jetty.io.SelectChannelEndPoint$2
				System.out.println("产生的任务是什么: " + task.getClass().getName());

            }
			if (dispatch) {
				if (!execute(this)) {
					task = null;
				}
            }

			if (task != null) {// 处理所有的请求--起始点
				System.out.println("开始执行的任务为: " + task.getClass().getName());
				task.run();
			}

			try (Lock locked = _locker.lock()) {
				if (_producing || _idle) {
                    break;
				}
                _producing = true;
            }
        }

    }
    public Boolean isIdle() {
        try (Lock locked = _locker.lock()) {
            return _idle;
        }
    }

	// ****************************************************************************
    @Override
	public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("EPC ");
        try (Lock locked = _locker.lock()) {
            builder.append(_idle ? "Idle/" : "");
            builder.append(_producing ? "Prod/" : "");
            builder.append(_pending ? "Pend/" : "");
            builder.append(_execute ? "Exec/" : "");
        }
        builder.append(_producer);
        return builder.toString();
    }

	// ****************************************************************************
    private class RunExecute implements Runnable {
        @Override
        public void run() {
            execute();
        }
    }

	// ****************************************************************************
    public static class Factory implements ExecutionStrategy.Factory {
        @Override
        public ExecutionStrategy newExecutionStrategy(Producer producer, Executor executor) {
            return new ExecuteProduceConsume(producer, executor);
        }
    }
}
