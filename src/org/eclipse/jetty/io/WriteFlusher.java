package org.eclipse.jetty.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritePendingException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

abstract public class WriteFlusher {// HTTP执行写操作

    private static final Logger LOG = Log.getLogger(WriteFlusher.class);
    private static final boolean DEBUG = LOG.isDebugEnabled(); // Easy for the compiler to remove the code if DEBUG==false
    private static final ByteBuffer[] EMPTY_BUFFERS = new ByteBuffer[]{BufferUtil.EMPTY_BUFFER};
    private static final EnumMap<StateType, Set<StateType>> __stateTransitions = new EnumMap<>(StateType.class);
    private static final State __IDLE = new IdleState();
    private static final State __WRITING = new WritingState();
    private static final State __COMPLETING = new CompletingState();
    private final EndPoint _endPoint;
    private final AtomicReference<State> _state = new AtomicReference<>();

	static {
        // fill the state machine
        __stateTransitions.put(StateType.IDLE, EnumSet.of(StateType.WRITING));
        __stateTransitions.put(StateType.WRITING, EnumSet.of(StateType.IDLE, StateType.PENDING, StateType.FAILED));
		__stateTransitions.put(StateType.PENDING, EnumSet.of(StateType.COMPLETING, StateType.IDLE));
        __stateTransitions.put(StateType.COMPLETING, EnumSet.of(StateType.IDLE, StateType.PENDING, StateType.FAILED));
        __stateTransitions.put(StateType.FAILED, EnumSet.of(StateType.IDLE));
    }

	protected WriteFlusher(EndPoint endPoint) {
        _state.set(__IDLE);
        _endPoint = endPoint;
    }

	private enum StateType {
        IDLE,
        WRITING,
        PENDING,
        COMPLETING,
        FAILED
    }

	private boolean updateState(State previous, State next) {
		if (!isTransitionAllowed(previous, next)) {
			throw new IllegalStateException();
		}
        boolean updated = _state.compareAndSet(previous, next);
		if (DEBUG) {
			LOG.debug("update {}:{}{}{}", this, previous, updated ? "-->" : "!->", next);
		}
        return updated;
    }

	private void fail(PendingState pending) {
        State current = _state.get();
		if (current.getType() == StateType.FAILED) {
            FailedState failed=(FailedState)current;
			if (updateState(failed, __IDLE)) {
                pending.fail(failed.getCause());
                return;
            }
        }
        throw new IllegalStateException();
    }
	private void ignoreFail() {
        State current = _state.get();
		while (current.getType() == StateType.FAILED) {
			if (updateState(current, __IDLE)) {
				return;
			}
            current = _state.get();
        }
    }

	private boolean isTransitionAllowed(State currentState, State newState) {
        Set<StateType> allowedNewStateTypes = __stateTransitions.get(currentState.getType());
		if (!allowedNewStateTypes.contains(newState.getType())) {
            LOG.warn("{}: {} -> {} not allowed", this, currentState, newState);
            return false;
        }
        return true;
    }

	private static class State {
        private final StateType _type;

		private State(StateType stateType) {
            _type = stateType;
        }

		public StateType getType() {
            return _type;
        }

        @Override
		public String toString() {
            return String.format("%s", _type);
        }
    }

	private static class IdleState extends State {
		private IdleState() {
            super(StateType.IDLE);
        }
    }

	private static class WritingState extends State {
		private WritingState() {
            super(StateType.WRITING);
        }
    }

	private static class FailedState extends State {
        private final Throwable _cause;

		private FailedState(Throwable cause) {
            super(StateType.FAILED);
            _cause=cause;
        }

		public Throwable getCause() {
            return _cause;
        }
    }
	private static class CompletingState extends State {
		private CompletingState() {
            super(StateType.COMPLETING);
        }
    }
	private class PendingState extends State {
        private final Callback _callback;
        private final ByteBuffer[] _buffers;

		private PendingState(ByteBuffer[] buffers, Callback callback) {
            super(StateType.PENDING);
            _buffers = buffers;
            _callback = callback;
        }

		public ByteBuffer[] getBuffers() {
            return _buffers;
        }

		protected boolean fail(Throwable cause) {
			if (_callback != null) {
                _callback.failed(cause);
                return true;
            }
            return false;
        }

		protected void complete() {
            if (_callback!=null)
                _callback.succeeded();
        }
        
		boolean isCallbackNonBlocking() {
            return _callback!=null && _callback.isNonBlocking();
        }
    }

	public boolean isCallbackNonBlocking() {
        State s = _state.get();
        return (s instanceof PendingState) && ((PendingState)s).isCallbackNonBlocking();
    }

    abstract protected void onIncompleteFlush();

	public void write(Callback callback, ByteBuffer... buffers) throws WritePendingException {
		if (DEBUG) {
			LOG.debug("write: {} {}", this, BufferUtil.toDetailString(buffers));
		}
		if (!updateState(__IDLE, __WRITING)) {
			throw new WritePendingException();
		}

		try {
            buffers=flush(buffers);

            // if we are incomplete?
			if (buffers != null) {
				if (DEBUG) {
					LOG.debug("flushed incomplete");
				}
                PendingState pending=new PendingState(buffers, callback);
				if (updateState(__WRITING, pending)) {
					onIncompleteFlush();
				} else {
					fail(pending);
				}
                return;
            }

            // If updateState didn't succeed, we don't care as our buffers have been written
			if (!updateState(__WRITING, __IDLE)) {
				ignoreFail();
			}
			if (callback != null) {
				callback.succeeded();
			}
		} catch (IOException e) {
            if (DEBUG){
            	LOG.debug("write exception", e);
            }
			if (updateState(__WRITING, __IDLE)) {
				if (callback != null) {
					callback.failed(e);
				}
			} else {
				fail(new PendingState(buffers, callback));
            }
        }
    }

	public void completeWrite() {
		if (DEBUG) {
			LOG.debug("completeWrite: {}", this);
		}

        State previous = _state.get();

		if (previous.getType() != StateType.PENDING) {
			return; // failure already handled.
		}

        PendingState pending = (PendingState)previous;
		if (!updateState(pending, __COMPLETING)) {
			return; // failure already handled.
		}

		try {
            ByteBuffer[] buffers = pending.getBuffers();

            buffers=flush(buffers);

            // if we are incomplete?
			if (buffers != null) {
				if (DEBUG) {
					LOG.debug("flushed incomplete {}", BufferUtil.toDetailString(buffers));
				}
				if (buffers != pending.getBuffers()) {
					pending = new PendingState(buffers, pending._callback);
				}
				if (updateState(__COMPLETING, pending)) {
					onIncompleteFlush();
				} else {
					fail(pending);
				}
                return;
            }

            // If updateState didn't succeed, we don't care as our buffers have been written
			if (!updateState(__COMPLETING, __IDLE)) {
				ignoreFail();
			}
            pending.complete();
		} catch (IOException e) {
			if (DEBUG) {
				LOG.debug("completeWrite exception", e);
			}
			if (updateState(__COMPLETING, __IDLE)) {
				pending.fail(e);
			} else {
				fail(pending);
			}
        }
    }

	protected ByteBuffer[] flush(ByteBuffer[] buffers) throws IOException {
        boolean progress=true;
		while (progress && buffers != null) {
			int before = buffers.length == 0 ? 0 : buffers[0].remaining();
            boolean flushed=_endPoint.flush(buffers);
			int r = buffers.length == 0 ? 0 : buffers[0].remaining();
			if (LOG.isDebugEnabled()) {
				LOG.debug("Flushed={} {}/{}+{} {}", flushed, before - r, before, buffers.length - 1, this);
			}
            
			if (flushed) {
				return null;
			}
            
			progress = before != r;
            
			int not_empty = 0;
			while (r == 0) {
				if (++not_empty == buffers.length) {
					buffers = null;
					not_empty = 0;
                    break;
                }
				progress = true;
				r = buffers[not_empty].remaining();
            }

			if (not_empty > 0) {
				buffers = Arrays.copyOfRange(buffers, not_empty, buffers.length);
			}
        }        

		if (LOG.isDebugEnabled()) {
			LOG.debug("!fully flushed {}", this);
		}

		return buffers == null ? EMPTY_BUFFERS : buffers;
    }

	public boolean onFail(Throwable cause) {
		while (true) {
			State current = _state.get();
			switch (current.getType()) {
                case IDLE:
                case FAILED:
                	if (DEBUG) {
                		LOG.debug("ignored: {} {}", this, cause);
                	}
                    return false;
                case PENDING:
                    if (DEBUG) {
                    	LOG.debug("failed: {} {}", this, cause);
                    }
                    PendingState pending = (PendingState)current;
                    if (updateState(pending, __IDLE))
                        return pending.fail(cause);
                    break;
                default:
					if (DEBUG) {
						LOG.debug("failed: {} {}", this, cause);
					}
					if (updateState(current, new FailedState(cause))) {
						return false;
					}
                    break;
            }
        }
    }

	public void onClose() {
		if (_state.get() == __IDLE) {
			return;
		}
        onFail(new ClosedChannelException());
    }

	boolean isIdle() {
        return _state.get().getType() == StateType.IDLE;
    }

	public boolean isInProgress() {
		switch (_state.get().getType()) {
            case WRITING:
            case PENDING:
            case COMPLETING:
                return true;
            default:
                return false;
        }
    }

    @Override
	public String toString() {
        return String.format("WriteFlusher@%x{%s}", hashCode(), _state.get());
    }
    
	public String toStateString() {
		switch (_state.get().getType()) {
            case WRITING:
                return "W";
            case PENDING:
                return "P";
            case COMPLETING:
                return "C";
            case IDLE:
                return "-";
            case FAILED:
                return "F";
            default:
                return "?";
        }
    }
}
