package org.eclipse.jetty.util;

import java.nio.channels.ClosedChannelException;

import org.eclipse.jetty.util.thread.Locker;

public abstract class IteratingCallback implements Callback {

	private enum State {
		IDLE,
        PROCESSING,
        PENDING,
        CALLED,
        SUCCEEDED,
        FAILED,
        CLOSED
    }

	protected enum Action {
        IDLE,
        SCHEDULED,
        SUCCEEDED
    }

    private Locker _locker = new Locker();
    private State _state;
    private boolean _iterate;

	protected IteratingCallback() {
        _state = State.IDLE;
    }

	protected IteratingCallback(boolean needReset) {
        _state = needReset ? State.SUCCEEDED : State.IDLE;
    }

    /**
     * Method called by {@link #iterate()} to process the sub task.
     * <p>
     * Implementations must start the asynchronous execution of the sub task
     * (if any) and return an appropriate action:
     * </p>
     * <ul>
     * <li>{@link Action#IDLE} when no sub tasks are available for execution
     * but the overall job is not completed yet</li>
     * <li>{@link Action#SCHEDULED} when the sub task asynchronous execution
     * has been started</li>
     * <li>{@link Action#SUCCEEDED} when the overall job is completed</li>
     * </ul>
     *
     * @return the appropriate Action
     *
     * @throws Exception if the sub task processing throws
     */
    protected abstract Action process() throws Exception;

    /**
     * Invoked when the overall task has completed successfully.
     *
     * @see #onCompleteFailure(Throwable)
     */
    protected void onCompleteSuccess()
    {
    }

    /**
     * Invoked when the overall task has completed with a failure.
     * @param cause the throwable to indicate cause of failure
     *
     * @see #onCompleteSuccess()
     */
    protected void onCompleteFailure(Throwable cause)
    {
    }

    /**
     * This method must be invoked by applications to start the processing
     * of sub tasks.  It can be called at any time by any thread, and it's
     * contract is that when called, then the {@link #process()} method will
     * be called during or soon after, either by the calling thread or by
     * another thread.
     */
    public void iterate()
    {
        boolean process=false;

        loop: while (true)
        {
            try (Locker.Lock lock = _locker.lock())
            {
                switch (_state)
                {
                    case PENDING:
                    case CALLED:
                        // process will be called when callback is handled
                        break loop;

                    case IDLE:
                        _state=State.PROCESSING;
                        process=true;
                        break loop;

                    case PROCESSING:
                        _iterate=true;
                        break loop;

                    case FAILED:
                    case SUCCEEDED:
                        break loop;

                    case CLOSED:
                    default:
                        throw new IllegalStateException(toString());
                }
            }
        }
        if (process)
            processing();
    }

    private void processing()
    {
        // This should only ever be called when in processing state, however a failed or close call
        // may happen concurrently, so state is not assumed.

        boolean on_complete_success=false;

        // While we are processing
        processing: while (true)
        {
            // Call process to get the action that we have to take.
            Action action;
            try
            {
                action = process();
            }
            catch (Throwable x)
            {
                failed(x);
                break processing;
            }

            // acted on the action we have just received
            try(Locker.Lock lock = _locker.lock())
            {
                switch (_state)
                {
                    case PROCESSING:
                    {
                        switch (action)
                        {
                            case IDLE:
                            {
                                // Has iterate been called while we were processing?
                                if (_iterate)
                                {
                                    // yes, so skip idle and keep processing
                                    _iterate=false;
                                    _state=State.PROCESSING;
                                    continue processing;
                                }

                                // No, so we can go idle
                                _state=State.IDLE;
                                break processing;
                            }

                            case SCHEDULED:
                            {
                                _state=State.PENDING;
                                break processing;
                            }
                            case SUCCEEDED:
                            {
                                _iterate=false;
                                _state=State.SUCCEEDED;
                                on_complete_success=true;
                                break processing;
                            }
                            default:
                                throw new IllegalStateException(String.format("%s[action=%s]", this, action));
                        }
                    }
                    case CALLED:
                    {
					switch (action) {
                            case SCHEDULED:
                            {
                                _state=State.PROCESSING;
                                continue processing;
                            }
                            default:
                                throw new IllegalStateException(String.format("%s[action=%s]", this, action));
                        }
                    }
                    case SUCCEEDED:
                    case FAILED:
                    case CLOSED:
                        break processing;
                    case IDLE:
                    case PENDING:
                    default:
                        throw new IllegalStateException(String.format("%s[action=%s]", this, action));
                }
            }
        }
		if (on_complete_success) {
			onCompleteSuccess();
		}
    }
    @Override
	public void succeeded() {
        boolean process=false;
		try (Locker.Lock lock = _locker.lock()) {
			switch (_state) {
                case PROCESSING:
                {
                    _state=State.CALLED;
                    break;
                }
                case PENDING:
                {
                    _state=State.PROCESSING;
                    process=true;
                    break;
                }
                case CLOSED:
                case FAILED:
                {
                    break;
                }
                default:
                {
                    throw new IllegalStateException(toString());
                }
            }
        }
		if (process) {
			processing();
		}
    }
    @Override
	public void failed(Throwable x) {
        boolean failure=false;
		try (Locker.Lock lock = _locker.lock()) {
			switch (_state) {
                case SUCCEEDED:
                case FAILED:
                case IDLE:
                case CLOSED:
                case CALLED:
                    break;
                case PENDING:
			case PROCESSING: {
                    _state=State.FAILED;
                    failure=true;
                    break;
                }
                default:
                    throw new IllegalStateException(toString());
            }
        }
		if (failure) {
			onCompleteFailure(x);
		}
    }

	public void close() {
        boolean failure=false;
		try (Locker.Lock lock = _locker.lock()) {
			switch (_state) {
                case IDLE:
                case SUCCEEDED:
                case FAILED:
                    _state=State.CLOSED;
                    break;
                case CLOSED:
                    break;
                default:
                    _state=State.CLOSED;
                    failure=true;
            }
        }
		if (failure) {
			onCompleteFailure(new ClosedChannelException());
		}
    }
	boolean isIdle() {
		try (Locker.Lock lock = _locker.lock()) {
            return _state == State.IDLE;
        }
    }
	public boolean isClosed() {
		try (Locker.Lock lock = _locker.lock()) {
            return _state == State.CLOSED;
        }
    }
	public boolean isFailed() {
		try (Locker.Lock lock = _locker.lock()) {
            return _state == State.FAILED;
        }
    }
	public boolean isSucceeded() {
		try (Locker.Lock lock = _locker.lock()) {
            return _state == State.SUCCEEDED;
        }
    }
	public boolean reset() {
		try (Locker.Lock lock = _locker.lock()) {
			switch (_state) {
                case IDLE:
                    return true;
                case SUCCEEDED:
                case FAILED:
                    _iterate=false;
                    _state=State.IDLE;
                    return true;
                default:
                    return false;
            }
        }
    }
    @Override
	public String toString() {
        return String.format("%s[%s]", super.toString(), _state);
    }
}
