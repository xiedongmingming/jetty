package org.eclipse.jetty.util;

public interface Callback {// 回调接口(主要用于通道产生的事件)

	// *******************************************************************
	Callback NOOP = new Callback() {// 空实现???
		// 由于提供了默认接口实现,所以可以通过NEW来创建
	};

	// *******************************************************************
	// 共3个接口函数
	default void succeeded() {// 操作成功

    }
	default void failed(Throwable x) {// 操作失败--ClosedChannelException

    }
	default boolean isNonBlocking() {// 表示默认是阻塞的
        return false;
    }

	// *******************************************************************
	// 下面是对该接口的扩展
	interface NonBlocking extends Callback {//
        @Override
		default boolean isNonBlocking() {
			return true;
        }
    }
	class Nested implements Callback {// 这个是类

        private final Callback callback;

		// ********************************************
		public Nested(Callback callback) {
            this.callback = callback;
        }
		public Nested(Nested nested) {
            this.callback = nested.callback;
        }

		// ********************************************
        @Override
		public void succeeded() {
            callback.succeeded();
        }
        @Override
		public void failed(Throwable x) {
            callback.failed(x);
        }
        @Override
		public boolean isNonBlocking() {
            return callback.isNonBlocking();
        }
		// ********************************************
    }
    @Deprecated
	class Adapter implements Callback {

    }
	// *******************************************************************
}
