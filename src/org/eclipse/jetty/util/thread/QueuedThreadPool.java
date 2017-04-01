package org.eclipse.jetty.util.thread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ThreadPool.SizedThreadPool;

public class QueuedThreadPool extends AbstractLifeCycle implements SizedThreadPool, Dumpable {// 线程池中的所有线程都运行同一个任务
    
	// 线程池:每个服务都需要一个线程池(用于服务的初始化)

	// 1、不是生命周期容器不用启动其他组件

	private static final Logger LOG = Log.getLogger(QueuedThreadPool.class);

	private final AtomicLong _lastShrink = new AtomicLong();// 上一次缩减线程池的时间戳

	private final ConcurrentHashSet<Thread> _threads = new ConcurrentHashSet<>();// 保存线程池中所有启动的线程(对应--_threadsStarted)

	private final Object _joinLock = new Object();// 用于该线程池运行同步

	// ****************************************************************************************
	private final AtomicInteger _threadsStarted = new AtomicInteger();// 表示线程池中启动的线程的数量--只在启动新线程时增加
	private final AtomicInteger _threadsIdle = new AtomicInteger();// 表示线程池中空闲的线程的数量(也是启动的线程)
	// ****************************************************************************************
	private int _idleTimeout;// 表示当线程空闲时间超过该值时退出

	private int _maxThreads;
	private int _minThreads;

	private final BlockingQueue<Runnable> _jobs;// 阻塞队列(所有由该线程池负责执行的任务)--读取写入是阻塞式的

	private final ThreadGroup _threadGroup;// 线程组--用于协同多个线程的工作(所有池中的线程都以此作为组)
	// ****************************************************************************************
    public QueuedThreadPool() {
        this(200);
    }
	public QueuedThreadPool(@Name("maxThreads") int maxThreads) {// 指定线程池的大小
        this(maxThreads, 8);
    }
    public QueuedThreadPool(@Name("maxThreads") int maxThreads,  @Name("minThreads") int minThreads) {
        this(maxThreads, minThreads, 60000);
    }
    public QueuedThreadPool(@Name("maxThreads") int maxThreads,  @Name("minThreads") int minThreads, @Name("idleTimeout")int idleTimeout) {
        this(maxThreads, minThreads, idleTimeout, null);
    }
    public QueuedThreadPool(@Name("maxThreads") int maxThreads, @Name("minThreads") int minThreads, @Name("idleTimeout") int idleTimeout, @Name("queue") BlockingQueue<Runnable> queue) {
        this(maxThreads, minThreads, idleTimeout, queue, null);
    }
    public QueuedThreadPool(@Name("maxThreads") int maxThreads, @Name("minThreads") int minThreads, @Name("idleTimeout") int idleTimeout, @Name("queue") BlockingQueue<Runnable> queue, @Name("threadGroup") ThreadGroup threadGroup) {

		// 默认最大线程数为:200
		// 默认最小线程数为:8
		// 默认闲置超时时间为:60000
		// 默认待运行队列实现为:BlockingArrayQueue
		// 默认线程池所在的线程组为空

		setMinThreads(minThreads);
        setMaxThreads(maxThreads);

        setIdleTimeout(idleTimeout);

		setStopTimeout(5000);// 父类中实现用于设置该该线程池停止超时时间

		if (queue == null) {// 待执行任务队列的大小

			int capacity = Math.max(_minThreads, 8);// 表示不能小于8

            queue = new BlockingArrayQueue<>(capacity, capacity);

        }

        _jobs = queue;

        _threadGroup = threadGroup;

    }

	// ****************************************************************************************
	// 下面是实现的接口: SizedThreadPool
	@Override
	public int getMinThreads() {
		return _minThreads;
	}
	@Override
	public int getMaxThreads() {
		return _maxThreads;
	}
    @Override
    public void setMinThreads(int minThreads) {
        _minThreads = minThreads;
		if (_minThreads > _maxThreads) {// 调整最大线程数量
			_maxThreads = _minThreads;
		}
		int threads = _threadsStarted.get();// 当前已经启动的线程数量
		if (isStarted() && threads < _minThreads) {// 必须是线程池已经启动的情况下(当不满足最小启动数量时启动额外的线程)
			startThreads(_minThreads - threads);// 另外启动的数量
		}
    }
	@Override
	public void setMaxThreads(int maxThreads) {// 确保最大值不变来调整最小值
		_maxThreads = maxThreads;
		if (_minThreads > _maxThreads) {// 调整最小线程数量
			_minThreads = _maxThreads;
		}
	}
	// ThreadPool
	@Override
	public void join() throws InterruptedException {// 主线程会在此处阻塞
		synchronized (_joinLock) {
			while (isRunning()) {// 表示线程池正在运行
				_joinLock.wait();// 阻塞等待(其它地方调用对应的停止等待函数)
			}
		}
		while (isStopping()) {// 正在停止中
			Thread.sleep(1);
		}
	}
	@Override
	public int getThreads() {// 表示已经启动的线程--包括空闲的线程
		return _threadsStarted.get();
	}
	@Override
	public int getIdleThreads() {// 也是启动的线程
		return _threadsIdle.get();
	}
	@Override
	public boolean isLowOnThreads() {// 用于衡量线程池的处理能力是否足够:表示剩余的全部线程数量(已经启动的空闲线程和还可以启动的线程数量减去待运行任务的数量)
		// 第一个表示最大线程数
		// 第二个表示已经启动的线程数
		// 第三个表示已经启动但出于空闲状态的线程数
		// 第四个表示待运行的任务队列
		// 第五个表示线程富余量限制
		return getMaxThreads() - getThreads() + getIdleThreads() - getQueueSize() <= getLowThreadsThreshold();
	}
	// Executor
	@Override
	public void execute(Runnable job) {// 用于该线程池运行外部提交的任务

		if (!isRunning() || !_jobs.offer(job)) {// 将待运行的任务投放到执行队列中(立即返回)

			LOG.warn("{} rejected {}", this, job);

			throw new RejectedExecutionException(job.toString());

		} else {// (当上面投放成功时)确保有线程运行该任务

			if (getThreads() == 0) {
				startThreads(1);
			}

		}
	}
	// ****************************************************************************************
    @Override
    protected void doStart() throws Exception {

		super.doStart();// 什么都不做

		_threadsStarted.set(0);

        startThreads(_minThreads);
    }
    @Override
    protected void doStop() throws Exception {

		super.doStop();// 什么都不做

		long timeout = getStopTimeout();// 表示停止该线程池的超时时间

		BlockingQueue<Runnable> jobs = getQueue();// 待运行的任务队列
		if (timeout <= 0) {// 直接清空
            jobs.clear();
		}

		Runnable noop = () -> {};
        for (int i = _threadsStarted.get(); i-- > 0; ) {
			jobs.offer(noop);// 唤醒空闲线程
		}

        long stopby = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout) / 2;

		for (Thread thread : _threads) {// 处理所有已启动的线程

			long canwait = TimeUnit.NANOSECONDS.toMillis(stopby - System.nanoTime());

            if (canwait > 0) {
                thread.join(canwait);
			}
        }

		if (_threadsStarted.get() > 0) {// 中断线程
            for (Thread thread : _threads) {
                thread.interrupt();
			}
		}

        stopby = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout) / 2;

        for (Thread thread : _threads) {

            long canwait = TimeUnit.NANOSECONDS.toMillis(stopby - System.nanoTime());

            if (canwait > 0) {
				thread.join(canwait);
			}
        }

        Thread.yield();

        int size = _threads.size();
        if (size > 0) {
            Thread.yield();
        }

        synchronized (_joinLock) {
			_joinLock.notifyAll();// 唤醒JOIN函数
        }
    }
	// ****************************************************************************************
	public int getBusyThreads() {// 表示启动的线程减去空闲的线程
		return getThreads() - getIdleThreads();
	}
	public int getQueueSize() {
		return _jobs.size();
	}
	public void setIdleTimeout(int idleTimeout) {// 空闲线程的超时时间--时间到则删除或继续等待任务(不可伸缩)
		_idleTimeout = idleTimeout;
	}
	public int getIdleTimeout() {
		return _idleTimeout;
	}
	// ****************************************************************************************
	private String _name = "qtp" + hashCode();// 线程池中的每个线程的名字的前缀--只能在线程池运行之前设置
	public void setName(String name) {
		if (isRunning()) {
			throw new IllegalStateException("started");
		}
		_name = name;
	}
	public String getName() {
		return _name;
	}
	// ****************************************************************************************
	private int _priority = Thread.NORM_PRIORITY;// 指示该线程池中的线程的优先级
	public void setThreadsPriority(int priority) {
		_priority = priority;
	}
	public int getThreadsPriority() {// 线程池中线程的优先级
		return _priority;
	}
	// ****************************************************************************************
	private boolean _daemon = false;// 指示该线程池中的线程是否以后台模式运行
	public void setDaemon(boolean daemon) {
		_daemon = daemon;
	}
	public boolean isDaemon() {// 判断线程池中的线程是否以后台模式运行
		return _daemon;
	}
	// ****************************************************************************************
	private boolean _detailedDump = false;
	public boolean isDetailedDump() {
		return _detailedDump;
	}
	public void setDetailedDump(boolean detailedDump) {
		_detailedDump = detailedDump;
	}
	// ****************************************************************************************
	private int _lowThreadsThreshold = 1;// 表示该线程池线程富余量限制
	public int getLowThreadsThreshold() {
		return _lowThreadsThreshold;
	}
	public void setLowThreadsThreshold(int lowThreadsThreshold) {
		_lowThreadsThreshold = lowThreadsThreshold;
	}
	// ****************************************************************************************
	protected Thread newThread(Runnable runnable) {
		return new Thread(_threadGroup, runnable);
	}
	private Runnable idleJobPoll() throws InterruptedException {
		return _jobs.poll(_idleTimeout, TimeUnit.MILLISECONDS);
	}
	//****************************************************************************************
	private boolean startThreads(int threadsToStart) {// 启动指定数量的线程

		// 注意:只有一个变量数值发生变化

		while (threadsToStart > 0 && isRunning()) {// 第二项表示线程池正在运行中

			int threads = _threadsStarted.get();

			if (threads >= _maxThreads) {// 已经超过了最大限制
				return false;
			}

			if (!_threadsStarted.compareAndSet(threads, threads + 1)) {// (若当前值等于第一个参数值时)表示数量加一
				continue;// 不成功
			}

			boolean started = false;// 新线程是否启动成功

			try {

				Thread thread = newThread(_runnable);// 这参数是什么????

				thread.setDaemon(isDaemon());
                thread.setPriority(getThreadsPriority());
				thread.setName(_name + "-" + thread.getId());// 线程名称--线程的ID

				_threads.add(thread);

				thread.start();// 启动

				started = true;

				--threadsToStart;

            } finally {

                if (!started) {
					_threadsStarted.decrementAndGet();// 表示如果没有启动成功时则恢复数量
				}
            }
        }

        return true;
    }

	//****************************************************************************************
	// 下面是实现的接口: Dumpable
    @Override
    public String dump() {
        return ContainerLifeCycle.dump(this);
    }
    @Override
    public void dump(Appendable out, String indent) throws IOException {

        List<Object> threads = new ArrayList<>(getMaxThreads());

        for (final Thread thread : _threads) {

            final StackTraceElement[] trace = thread.getStackTrace();

            boolean inIdleJobPoll = false;

            for (StackTraceElement t : trace) {

				if ("idleJobPoll".equals(t.getMethodName())) {// 找到调用栈中指定的方法
                    inIdleJobPoll = true;
                    break;
                }
            }

            final boolean idle = inIdleJobPoll;

            if (isDetailedDump()) {

                threads.add(new Dumpable() {

                    @Override
                    public void dump(Appendable out, String indent) throws IOException {

                        out.append(String.valueOf(thread.getId())).append(' ').append(thread.getName()).append(' ').append(thread.getState().toString()).append(idle ? " IDLE" : "");

                        if (thread.getPriority() != Thread.NORM_PRIORITY) {
                            out.append(" prio=").append(String.valueOf(thread.getPriority()));
						}

                        out.append(System.lineSeparator());

                        if (!idle) {
                            ContainerLifeCycle.dump(out, indent, Arrays.asList(trace));
						}
                    }

                    @Override
                    public String dump() {
                        return null;
                    }

                });
            } else {

                int p = thread.getPriority();

                threads.add(thread.getId() + " " + thread.getName() + " " + thread.getState() + " @ " + (trace.length > 0 ? trace[0] : "???") + (idle ? " IDLE" : "") + (p == Thread.NORM_PRIORITY ? "" : (" prio=" + p)));
            }
        }

        List<Runnable> jobs = Collections.emptyList();

        if (isDetailedDump()) {
            jobs = new ArrayList<>(getQueue());
		}

        ContainerLifeCycle.dumpObject(out, this);
        ContainerLifeCycle.dump(out, indent, threads, jobs);
    }
	// ****************************************************************************************
	public String dumpThread(@Name("id") long id) {// the stack frames dump

		for (Thread thread : _threads) {// 打印指定线程栈的调用信息

			if (thread.getId() == id) {// 主线程的ID永远是1???

				StringBuilder buf = new StringBuilder();

				buf.append(thread.getId()).append(" ").append(thread.getName()).append(" ");
				buf.append(thread.getState()).append(":").append(System.lineSeparator());

				for (StackTraceElement element : thread.getStackTrace()) {
					buf.append("  at ").append(element.toString()).append(System.lineSeparator());
				}

				return buf.toString();
			}
		}
		return null;
	}
	// ****************************************************************************************
    @Override
    public String toString() {
        return String.format("%s{%s,%d<=%d<=%d,i=%d,q=%d}", _name, getState(), getMinThreads(), getThreads(), getMaxThreads(), getIdleThreads(), (_jobs == null ? -1 : _jobs.size()));
    }
	// ****************************************************************************************
	// 线程池中的每个线程都运行该任务
	private Runnable _runnable = new Runnable() {// 该线程池中的每个线程都运行该任务--用于执行任务队列中的任务

		@Override
        public void run() {

            boolean shrink = false;
            boolean ignore = false;

            try {

				Runnable job = _jobs.poll();// 返回并删除(立即返回)
				if (job != null && _threadsIdle.get() == 0) {// 表示空闲线程为0(无可运行的线程)--注意不是当前线程
					startThreads(1);// 重新启动一个
                }

				loop: while (isRunning()) {// 大循环执行

					while (job != null && isRunning()) {// 循环运行任务

						runJob(job);// 线程执行的起点

						if (Thread.interrupted()) {// 表示该线程被中断--退出执行
                            ignore = true;
							break loop;// 外层的循环
                        }

						job = _jobs.poll();// 下一个任务
                    }

					//********************************************************************
					// 运行到此表示所有待运行的任务都已经运行完了或直接没有获取到一个有效的任务
					try {// 空闲循环

						_threadsIdle.incrementAndGet();// 当前线程准备成为空闲线程

						while (isRunning() && job == null) {// 只会在没有任务执行的情况下运行下面的代码

							if (_idleTimeout <= 0) {// 表示没有设置空闲超时时间

								job = _jobs.take();// 无限期阻塞等待

							} else {

								final int size = _threadsStarted.get();

								if (size > _minThreads) {

									long last = _lastShrink.get();

                                    long now = System.nanoTime();

									if (last == 0 || (now - last) > TimeUnit.MILLISECONDS.toNanos(_idleTimeout)) {// 表示超时
                                        
										if (_lastShrink.compareAndSet(last, now) && _threadsStarted.compareAndSet(size, size - 1)) {

											shrink = true;// 需要退出

											break loop;// 外层的循环
                                        }
                                    }
                                }

								// ****************************************************
								// 运行到此表示不需要线程压缩
								job = idleJobPoll();// 超时阻塞获取任务

                            }
                        }
                    } finally {

						if (_threadsIdle.decrementAndGet() == 0) {// 每次运行完成后都需要检查下是否需要启动新的线程
                            startThreads(1);
                        }

                    }
				} // <<<<<<<<<<<<<<整个大循环>>>>>>>>>>>>>>
			} catch (InterruptedException e) {// 表示线程执行过程中发生了中断

				ignore = true;

                LOG.ignore(e);

			} catch (Throwable e) {// 其他异常状况

                LOG.warn(e);

			} finally {// 表示大循环退出了

                if (!shrink && isRunning()) {
                    if (!ignore) {
						LOG.warn("unexpected thread death: {} in {}", this, QueuedThreadPool.this);
					}
                    if (_threadsStarted.decrementAndGet() < getMaxThreads()) {//this is an unexpected thread death!
						startThreads(1);
					}
                }

				_threads.remove(Thread.currentThread());// 该线程执行完毕删除

            }
        }
    };
	protected void runJob(Runnable job) {// 表示线程池中的线程执行一个具体的任务
		job.run();// org.eclipse.jetty.server.AbstractConnector$Acceptor
    }
	protected BlockingQueue<Runnable> getQueue() {
        return _jobs;
    }
    @Deprecated
    public void setQueue(BlockingQueue<Runnable> queue) {
		throw new UnsupportedOperationException("use constructor injection");
    }
	public boolean interruptThread(@Name("id") long id) {
        for (Thread thread : _threads) {
            if (thread.getId() == id) {
				thread.interrupt();// 线程的中断 -- 表示给指定的线程发送中断请求
                return true;
            }
        }
        return false;
    }
}
