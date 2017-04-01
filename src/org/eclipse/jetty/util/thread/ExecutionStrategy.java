package org.eclipse.jetty.util.thread;

import java.lang.reflect.Constructor;
import java.util.concurrent.Executor;

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.strategy.ExecuteProduceConsume;

public interface ExecutionStrategy {// 执行策略--包括两个接口

    public void dispatch();
    public void execute();

	// ******************************************************************

	public interface Producer {// 生产器接口--生产一个任务
		Runnable produce();
	}
	public interface Rejectable {//
        public void reject();
    }

	public static interface Factory {// 静态接口

		public ExecutionStrategy newExecutionStrategy(Producer producer, Executor executor);

		public static Factory getDefault() {// 默认实现
            return DefaultExecutionStrategyFactory.INSTANCE;
        }

		@Deprecated
        public static ExecutionStrategy instanceFor(Producer producer, Executor executor) {
            return getDefault().newExecutionStrategy(producer, executor);
        }
    }

	public static class DefaultExecutionStrategyFactory implements Factory {// 为工厂类提供默认实现

		private static final Logger LOG = Log.getLogger(Factory.class);

        private static final Factory INSTANCE = new DefaultExecutionStrategyFactory();

		@SuppressWarnings("unchecked")
		@Override
        public ExecutionStrategy newExecutionStrategy(Producer producer, Executor executor) {

			String strategy = System.getProperty(producer.getClass().getName() + ".ExecutionStrategy");

			// System.out.println("<<<<<<<获取的策略为>>>>>>>: " + producer.getClass().getName() + ".ExecutionStrategy");

			if (strategy != null) {// 从配置文件中加载执行策略类

				try {

					Class<? extends ExecutionStrategy> c = Loader.loadClass(producer.getClass(), strategy);

                    Constructor<? extends ExecutionStrategy> m = c.getConstructor(Producer.class, Executor.class);

					LOG.info("use {} for {}", c.getSimpleName(), producer.getClass().getName());

					System.out.println("使用配置执行策略.................................");

                    return m.newInstance(producer, executor);

                } catch (Exception e) {
                    LOG.warn(e);
                }

            }

			// System.out.println("使用默认执行策略.................................");

			return new ExecuteProduceConsume(producer, executor);// 针对SELECTOR的返回的执行体
        }
    }
}
