package org.eclipse.jetty.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpGenerator;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.ChannelEndPoint;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.server.HttpChannelState.Action;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.SharedBlockingCallback.Blocker;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Scheduler;

public class HttpChannel implements Runnable, HttpOutput.Interceptor {

	// 这个类比较重要:
	// 1、关联了HTTP请求的REQUEST和RESPONSE
	// 2、

    private static final Logger LOG = Log.getLogger(HttpChannel.class);

	private final AtomicBoolean _committed = new AtomicBoolean();// 表示响应是否发送了
    private final AtomicInteger _requests = new AtomicInteger();

	private final Connector _connector;// ServerConnector
	private final Executor _executor;// QueuedThreadPool

	private final HttpConfiguration _configuration;// ????共用一个配置--HttpConfiguration
	private final EndPoint _endPoint;// SelectChannelEndPoint
	private final HttpTransport _transport;// HttpConnection
	private final HttpChannelState _state;// 表示一个HTTP通道的状态--HttpChannelState

	private final Request _request;// 请求
	private final Response _response;// 响应

    private MetaData.Response _committedMetaData;
    private RequestLog _requestLog;

    private long _written;

	public HttpChannel(Connector connector, HttpConfiguration configuration, EndPoint endPoint, HttpTransport transport) {// HttpChannelOverHttp.newHttpChannel

		_connector = connector;
        _configuration = configuration;
        _endPoint = endPoint;
        _transport = transport;

        _state = new HttpChannelState(this);
		_request = new Request(this, newHttpInput(_state));// 新建一个请求
		_response = new Response(this, newHttpOutput());// 新建一个响应

        _executor = connector == null ? null : connector.getServer().getThreadPool();
        _requestLog = connector == null ? null : connector.getServer().getRequestLog();

    }
    protected HttpInput newHttpInput(HttpChannelState state) {
        return new HttpInput(state);
    }
    protected HttpOutput newHttpOutput() {
        return new HttpOutput(this);
    }
    public HttpChannelState getState() {
        return _state;
    }
    public long getBytesWritten() {
        return _written;
    }
    public int getRequests() {
        return _requests.get();
    }
    public Connector getConnector() {
        return _connector;
    }
    public HttpTransport getHttpTransport() {
        return _transport;
    }
    public RequestLog getRequestLog() {
        return _requestLog;
    }
    public void setRequestLog(RequestLog requestLog) {
        _requestLog = requestLog;
    }
    public void addRequestLog(RequestLog requestLog) {
        if (_requestLog == null) {
            _requestLog = requestLog;
        } else if (_requestLog instanceof RequestLogCollection) {
            ((RequestLogCollection) _requestLog).add(requestLog);
        } else {
            _requestLog = new RequestLogCollection(_requestLog, requestLog);
		}
    }
    public MetaData.Response getCommittedMetaData() {
        return _committedMetaData;
    }
	public long getIdleTimeout() {
        return _endPoint.getIdleTimeout();
    }
	public void setIdleTimeout(long timeoutMs) {
        _endPoint.setIdleTimeout(timeoutMs);
    }
	public ByteBufferPool getByteBufferPool() {
        return _connector.getByteBufferPool();
    }
	public HttpConfiguration getHttpConfiguration() {
        return _configuration;
    }
    @Override
	public boolean isOptimizedForDirectBuffers() {
        return getHttpTransport().isOptimizedForDirectBuffers();
    }
	public Server getServer() {
        return _connector.getServer();
    }
	public Request getRequest() {
        return _request;
    }
	public Response getResponse() {
        return _response;
    }
	public EndPoint getEndPoint() {
        return _endPoint;
    }
	public InetSocketAddress getLocalAddress() {
        return _endPoint.getLocalAddress();
    }
	public InetSocketAddress getRemoteAddress() {
        return _endPoint.getRemoteAddress();
    }
	public void continue100(int available) throws IOException {
        throw new UnsupportedOperationException();
    }

	public void recycle() {
        _committed.set(false);
        _request.recycle();
        _response.recycle();
		_committedMetaData = null;
		_requestLog = _connector == null ? null : _connector.getServer().getRequestLog();
		_written = 0;
    }
	public void asyncReadFillInterested() {
    }

    @Override
	public void run() {// 由谁执行????
		handle();
    }

	// **********************************************************************************************************
	public boolean handle() {// 表示处理该HTTP通道的HTTP请求

		HttpChannelState.Action action = _state.handling();// 表示根据当前的状态获取下一步的动作

        loop: while (!getServer().isStopped()) {
            try {
                switch(action) {
                    case TERMINATED:
                    case WAIT:
                        break loop;

                    case DISPATCH: {//

						if (!_request.hasMetaData()) {
							throw new IllegalStateException("state=" + _state);
						}

                        _request.setHandled(false);
                        _response.getHttpOutput().reopen();// 设置输出状态

                        try {

                            _request.setDispatcherType(DispatcherType.REQUEST);

                            List<HttpConfiguration.Customizer> customizers = _configuration.getCustomizers();// 标准化相关(定制)

                            if (!customizers.isEmpty()) {

								for (HttpConfiguration.Customizer customizer : customizers) {

                                    customizer.customize(getConnector(), _configuration, _request);

                                    if (_request.isHandled()) {
                                    	break;
                                    }
                                }
                            }

                            if (!_request.isHandled()) {// 表示还未处理
                            	getServer().handle(this);
                            }

						} finally {
	                        _request.setDispatcherType(null);
	                    }
	                    break;
	                }
                    case ASYNC_DISPATCH: {

                        _request.setHandled(false);
                        _response.getHttpOutput().reopen();

                        try {
                            _request.setDispatcherType(DispatcherType.ASYNC);
                            getServer().handleAsync(this);
                        } finally {
                            _request.setDispatcherType(null);
                        }
                        break;
                    }
                    case ERROR_DISPATCH: {

                        Throwable ex = _state.getAsyncContextEvent().getThrowable();

                        Integer loop_detect = (Integer)_request.getAttribute("org.eclipse.jetty.server.ERROR_DISPATCH");

						if (loop_detect == null) {
							loop_detect = 1;
						} else {
							loop_detect = loop_detect + 1;
						}

                        _request.setAttribute("org.eclipse.jetty.server.ERROR_DISPATCH", loop_detect);

                        if (loop_detect > getHttpConfiguration().getMaxErrorDispatches()) {

                            LOG.warn("ERROR_DISPATCH loop detected on {} {}", _request, ex);

                            try {
                                _response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500);
                            } finally {
                                _state.errorComplete();
                            }

                            break loop;
                        }

                        _request.setHandled(false);
                        _response.resetBuffer();
                        _response.getHttpOutput().reopen();

                        String reason;

                        if (ex == null || ex instanceof TimeoutException) {
                            reason = "Async Timeout";
                        } else {
                            reason = HttpStatus.Code.INTERNAL_SERVER_ERROR.getMessage();
                            _request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ex);
                        }

                        _request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
                        _request.setAttribute(RequestDispatcher.ERROR_MESSAGE, reason);
                        _request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, _request.getRequestURI());

                        _response.setStatusWithReason(HttpStatus.INTERNAL_SERVER_ERROR_500, reason);

                        ErrorHandler eh = ErrorHandler.getErrorHandler(getServer(), _state.getContextHandler());
                        if (eh instanceof ErrorHandler.ErrorPageMapper) {
                            String error_page = ((ErrorHandler.ErrorPageMapper)eh).getErrorPage((HttpServletRequest)_state.getAsyncContextEvent().getSuppliedRequest());
                            if (error_page != null) {
                                _state.getAsyncContextEvent().setDispatchPath(error_page);
                            }
                        }

                        try {
                            _request.setDispatcherType(DispatcherType.ERROR);
                            getServer().handleAsync(this);
                        } finally {
                            _request.setDispatcherType(null);
                        }
                        break;
                    }

                    case READ_CALLBACK: {
                    	ContextHandler handler = _state.getContextHandler();
                        if (handler != null) {
                            handler.handle(_request, _request.getHttpInput());
                        } else {
                            _request.getHttpInput().run();
                        }
                        break;
                    }
                    case WRITE_CALLBACK:  {
                        ContextHandler handler = _state.getContextHandler();
                        if (handler != null) {
                            handler.handle(_request, _response.getHttpOutput());
                        } else {
                            _response.getHttpOutput().run();
                        }
                        break;
                    }
                    case ASYNC_ERROR: {
                        _state.onError();
                        break;
                    }
                    case COMPLETE: {
                        if (!_response.isCommitted() && !_request.isHandled()) {
                            _response.sendError(404);
                        } else {
                            _response.closeOutput();
                        }
                        _request.setHandled(true);
					_state.onComplete();
                        onCompleted();
                        break loop;
                    }
                    default: {
                        throw new IllegalStateException("state=" + _state);
                    }
                }
			} catch (EofException | QuietServletException | BadMessageException e) {
                handleException(e);
            } catch (Throwable e) {
                if ("ContinuationThrowable".equals(e.getClass().getSimpleName())) {
                    LOG.ignore(e);
                } else {
					if (_connector.isStarted()) {
						LOG.warn(String.valueOf(_request.getHttpURI()), e);
					} else {
						LOG.debug(String.valueOf(_request.getHttpURI()), e);
					}
                    handleException(e);
                }
            }
            action = _state.unhandle();
        }

		boolean suspended = action == Action.WAIT;
        return !suspended;
    }

	// **********************************************************************************************************
    protected void handleException(Throwable x) {
		if (_state.isAsyncStarted()) {
            // Handle exception via AsyncListener onError
            Throwable root = _state.getAsyncContextEvent().getThrowable();
			if (root == null) {
                _state.error(x);
			} else {
                // TODO Can this happen?  Should this just be ISE???
                // We've already processed an error before!
                root.addSuppressed(x);
                LOG.warn("Error while handling async error: ", root);
                abort(x);
                _state.errorComplete();
            }
		} else {
			try {
                // Handle error normally
                _request.setHandled(true);
                _request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, x);
                _request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, x.getClass());

				if (isCommitted()) {
                    abort(x);
				} else {
                    _response.setHeader(HttpHeader.CONNECTION.asString(), HttpHeaderValue.CLOSE.asString());

					if (x instanceof BadMessageException) {
                        BadMessageException bme = (BadMessageException)x;
                        _response.sendError(bme.getCode(), bme.getReason());
					} else if (x instanceof UnavailableException) {
						if (((UnavailableException) x).isPermanent()) {
							_response.sendError(HttpStatus.NOT_FOUND_404);
						} else {
							_response.sendError(HttpStatus.SERVICE_UNAVAILABLE_503);
						}
					} else {
						_response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500);
                    }
                }
			} catch (Throwable e) {
                abort(e);
            }
        }
    }

	public boolean isExpecting100Continue() {
        return false;
    }

	public boolean isExpecting102Processing() {
        return false;
    }

    @Override
	public String toString() {
        return String.format("%s@%x{r=%s,c=%b,a=%s,uri=%s}", getClass().getSimpleName(), hashCode(), _requests, _committed.get(), _state.getState(), _request.getHttpURI());
    }

	public void onRequest(MetaData.Request request) {
        _requests.incrementAndGet();
        _request.setTimeStamp(System.currentTimeMillis());
        HttpFields fields = _response.getHttpFields();
		if (_configuration.getSendDateHeader() && !fields.contains(HttpHeader.DATE)) {
			fields.put(_connector.getServer().getDateField());
		}
        _request.setMetaData(request);
    }

	public boolean onContent(HttpInput.Content content) {
        return _request.getHttpInput().addContent(content);
    }

	public boolean onRequestComplete() {
        return _request.getHttpInput().eof();
    }

	public void onCompleted() {
		if (_requestLog != null) {
			_requestLog.log(_request, _response);
		}

        _transport.onCompleted();
    }

    public boolean onEarlyEOF() {
        return _request.getHttpInput().earlyEOF();
    }

    public void onBadMessage(int status, String reason) {
        if (status < 400 || status > 599)
            status = HttpStatus.BAD_REQUEST_400;

        Action action;
        try {
           action=_state.handling();
        } catch(IllegalStateException e) {
            // The bad message cannot be handled in the current state, so throw
            // to hopefull somebody that can handle
            abort(e);
            throw new BadMessageException(status,reason);
        }

        try {
            if (action==Action.DISPATCH) {
                ByteBuffer content=null;
                HttpFields fields=new HttpFields();
                ErrorHandler handler=getServer().getBean(ErrorHandler.class);
                if (handler != null) {
                    content=handler.badMessageError(status,reason,fields);
				}
                sendResponse(new MetaData.Response(HttpVersion.HTTP_1_1,status,reason,fields,BufferUtil.length(content)),content ,true);
            }
        } catch (IOException e) {
            LOG.debug(e);
        } finally {
            // TODO: review whether it's the right state to check.
            if (_state.unhandle()==Action.COMPLETE) {
                _state.onComplete();
            } else {
                throw new IllegalStateException(); // TODO: don't throw from finally blocks !
			}
            onCompleted();
        }
    }

    protected boolean sendResponse(MetaData.Response info, ByteBuffer content, boolean complete, final Callback callback) {
        boolean committing = _committed.compareAndSet(false, true);
        if (committing) {
            //we need an info to commit
            if (info == null) {
                info = _response.newResponseMetaData();
			}
            commit(info);
            //wrap callback to process 100 responses
            final int status=info.getStatus();
            final Callback committed = (status<200&&status>=100)?new Commit100Callback(callback):new CommitCallback(callback);
            //committing write
            _transport.send(info, _request.isHead(), content, complete, committed);
        } else if (info == null) {//this is a normal write
            _transport.send(null,_request.isHead(), content, complete, callback);
        } else {
            callback.failed(new IllegalStateException("committed"));
        }
        return committing;
    }

    protected boolean sendResponse(MetaData.Response info, ByteBuffer content, boolean complete) throws IOException {
        try(Blocker blocker = _response.getHttpOutput().acquireWriteBlockingCallback()) {
            boolean committing = sendResponse(info,content,complete,blocker);
            blocker.block();
            return committing;
        } catch (Throwable failure) {
            abort(failure);
            throw failure;
        }
    }
    protected void commit (MetaData.Response info) {
        _committedMetaData=info;
    }
    public boolean isCommitted() {
        return _committed.get();
    }
    @Override
    public void write(ByteBuffer content, boolean complete, Callback callback) {
        _written+=BufferUtil.length(content);
        sendResponse(null,content,complete,callback);
    }
    @Override
	public HttpOutput.Interceptor getNextInterceptor() {
        return null;
    }
    protected void execute(Runnable task) {
        _executor.execute(task);
    }
    public Scheduler getScheduler() {
        return _connector.getScheduler();
    }
    public boolean useDirectBuffers() {
        return getEndPoint() instanceof ChannelEndPoint;
    }
    public void abort(Throwable failure) {
        _transport.abort(failure);
    }
    private class CommitCallback extends Callback.Nested {

        private CommitCallback(Callback callback) {
            super(callback);
        }

        @Override
        public void failed(final Throwable x) {
            if (x instanceof BadMessageException) {
                _transport.send(HttpGenerator.RESPONSE_500_INFO, false, null, true, new Callback.Nested(this) {
                    @Override
                    public void succeeded() {
                        super.failed(x);
                        _response.getHttpOutput().closed();
                    }
                    @Override
                    public void failed(Throwable th) {
                        _transport.abort(x);
                        super.failed(x);
                    }
                });
            } else {
                _transport.abort(x);
                super.failed(x);
            }
        }
    }
    private class Commit100Callback extends CommitCallback {

        private Commit100Callback(Callback callback) {
            super(callback);
        }

        @Override
        public void succeeded() {
            if (_committed.compareAndSet(true, false)) {
                super.succeeded();
			} else {
                super.failed(new IllegalStateException());
			}
        }
    }
}
