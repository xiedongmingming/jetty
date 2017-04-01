package org.eclipse.jetty.embedded;

import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class QueuedThreadPoolTest {

	public static void main(String[] args) throws Exception {

		QueuedThreadPool threadPool = new QueuedThreadPool();
		threadPool.start();

		new Thread(new Runnable() {

			@Override
			public void run() {

				while (true) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					System.out.println("添加一个任务到线程池中");
					threadPool.execute(new Runnable() {

						@Override
						public void run() {
							System.out.println("线程池中执行任务");
						}
					});
				}
			}

		}).run();

		threadPool.join();

	}

}
