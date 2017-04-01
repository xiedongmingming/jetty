package org.eclipse.jetty.util;

import org.eclipse.jetty.util.log.Log;

public interface Promise<C> {

    public abstract void succeeded(C result);
    public void failed(Throwable x);

	public static class Adapter<C> implements Promise<C> {
        @Override
		public void succeeded(C result) {
        }
        @Override
		public void failed(Throwable x) {
            Log.getLogger(this.getClass()).warn(x);
        }
    }

	public static abstract class Wrapper<W> implements Promise<W> {
        private final Promise<W> promise;

		public Wrapper(Promise<W> promise) {
            this.promise = promise;
        }

		public Promise<W> getPromise() {
            return promise;
        }
		public Promise<W> unwrap() {
            Promise<W> result = promise;
			while (true) {
				if (result instanceof Wrapper) {
					result = ((Wrapper<W>) result).unwrap();
				} else {
					break;
				}
            }
            return result;
        }
    }
}
