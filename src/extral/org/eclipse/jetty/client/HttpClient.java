package extral.org.eclipse.jetty.client;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

import extral.org.eclipse.jetty.client.api.AuthenticationStore;
import extral.org.eclipse.jetty.client.api.Connection;
import extral.org.eclipse.jetty.client.api.ContentResponse;
import extral.org.eclipse.jetty.client.api.Destination;
import extral.org.eclipse.jetty.client.api.Request;
import extral.org.eclipse.jetty.client.api.Response;
import extral.org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import extral.org.eclipse.jetty.client.util.FormContentProvider;

@ManagedObject("The HTTP client")
public class HttpClient extends ContainerLifeCycle {
	
    private static final Logger LOG = Log.getLogger(HttpClient.class);

    private final ConcurrentMap<Origin, HttpDestination> destinations = new ConcurrentHashMap<>();
    private final ProtocolHandlers handlers = new ProtocolHandlers();
    private final List<Request.Listener> requestListeners = new ArrayList<>();
    private final AuthenticationStore authenticationStore = new HttpAuthenticationStore();
    private final Set<ContentDecoder.Factory> decoderFactories = new ContentDecoderFactorySet();
    private final ProxyConfiguration proxyConfig = new ProxyConfiguration();
    private final HttpClientTransport transport;
    private final SslContextFactory sslContextFactory;
    private volatile CookieManager cookieManager;
    private volatile CookieStore cookieStore;
    private volatile Executor executor;
    private volatile ByteBufferPool byteBufferPool;
    private volatile Scheduler scheduler;
    private volatile SocketAddressResolver resolver;
    private volatile HttpField agentField = new HttpField(HttpHeader.USER_AGENT, "Jetty/" + Jetty.VERSION);
    private volatile boolean followRedirects = true;
    private volatile int maxConnectionsPerDestination = 64;
    private volatile int maxRequestsQueuedPerDestination = 1024;
    private volatile int requestBufferSize = 4096;
    private volatile int responseBufferSize = 16384;
    private volatile int maxRedirects = 8;
    private volatile SocketAddress bindAddress;
    private volatile long connectTimeout = 15000;
    private volatile long addressResolutionTimeout = 15000;
    private volatile long idleTimeout;
    private volatile boolean tcpNoDelay = true;
    private volatile boolean strictEventOrdering = false;
    private volatile HttpField encodingField;
    private volatile boolean removeIdleDestinations = false;
    private volatile boolean connectBlocking = false;

    public HttpClient() {
        this(null);
    }
    public HttpClient(SslContextFactory sslContextFactory) {
        this(new HttpClientTransportOverHTTP(), sslContextFactory);
    }
    public HttpClient(HttpClientTransport transport, SslContextFactory sslContextFactory) {
        this.transport = transport;
        this.sslContextFactory = sslContextFactory;
    }
    public HttpClientTransport getTransport() {
        return transport;
    }

	public SslContextFactory getSslContextFactory() {
        return sslContextFactory;
    }

    @Override
	protected void doStart() throws Exception {
		if (sslContextFactory != null) {
			addBean(sslContextFactory);
		}

        String name = HttpClient.class.getSimpleName() + "@" + hashCode();

		if (executor == null) {
            QueuedThreadPool threadPool = new QueuedThreadPool();
            threadPool.setName(name);
            executor = threadPool;
        }
        addBean(executor);

		if (byteBufferPool == null) {
			byteBufferPool = new MappedByteBufferPool();
		}
        addBean(byteBufferPool);

		if (scheduler == null) {
			scheduler = new ScheduledExecutorScheduler(name + "-scheduler", false);
		}
        addBean(scheduler);

        transport.setHttpClient(this);
        addBean(transport);

		if (resolver == null) {
			resolver = new SocketAddressResolver.Async(executor, scheduler, getAddressResolutionTimeout());
		}
        addBean(resolver);

        handlers.put(new ContinueProtocolHandler());
        handlers.put(new RedirectProtocolHandler(this));
        handlers.put(new WWWAuthenticationProtocolHandler(this));
        handlers.put(new ProxyAuthenticationProtocolHandler(this));

        decoderFactories.add(new GZIPContentDecoder.Factory());

        cookieManager = newCookieManager();
        cookieStore = cookieManager.getCookieStore();

        super.doStart();
    }

	private CookieManager newCookieManager() {
        return new CookieManager(getCookieStore(), CookiePolicy.ACCEPT_ALL);
    }

    @Override
	protected void doStop() throws Exception {
        cookieStore.removeAll();
        decoderFactories.clear();
        handlers.clear();

		for (HttpDestination destination : destinations.values()) {
			destination.close();
		}
        destinations.clear();

        requestListeners.clear();
        authenticationStore.clearAuthentications();
        authenticationStore.clearAuthenticationResults();

        super.doStop();
    }

	public List<Request.Listener> getRequestListeners() {
        return requestListeners;
    }

	public CookieStore getCookieStore() {
        return cookieStore;
    }

	public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = Objects.requireNonNull(cookieStore);
        this.cookieManager = newCookieManager();
    }

	CookieManager getCookieManager() {
        return cookieManager;
    }

	public AuthenticationStore getAuthenticationStore() {
        return authenticationStore;
    }

	public Set<ContentDecoder.Factory> getContentDecoderFactories() {
        return decoderFactories;
    }

	public ContentResponse GET(String uri) throws InterruptedException, ExecutionException, TimeoutException {
        return GET(URI.create(uri));
    }

	public ContentResponse GET(URI uri) throws InterruptedException, ExecutionException, TimeoutException {
        return newRequest(uri).send();
    }

	public ContentResponse FORM(String uri, Fields fields)
			throws InterruptedException, ExecutionException, TimeoutException {
        return FORM(URI.create(uri), fields);
    }

	public ContentResponse FORM(URI uri, Fields fields)
			throws InterruptedException, ExecutionException, TimeoutException {
        return POST(uri).content(new FormContentProvider(fields)).send();
    }

	public Request POST(String uri) {
        return POST(URI.create(uri));
    }

	public Request POST(URI uri) {
        return newRequest(uri).method(HttpMethod.POST);
    }

	public Request newRequest(String host, int port) {
        return newRequest(new Origin("http", host, port).asString());
    }

	public Request newRequest(String uri) {
        return newRequest(URI.create(uri));
    }

	public Request newRequest(URI uri) {
        return newHttpRequest(newConversation(), uri);
    }

	protected Request copyRequest(HttpRequest oldRequest, URI newURI) {
        Request newRequest = newHttpRequest(oldRequest.getConversation(), newURI);
        newRequest.method(oldRequest.getMethod())
                .version(oldRequest.getVersion())
                .content(oldRequest.getContent())
                .idleTimeout(oldRequest.getIdleTimeout(), TimeUnit.MILLISECONDS)
                .timeout(oldRequest.getTimeout(), TimeUnit.MILLISECONDS)
                .followRedirects(oldRequest.isFollowRedirects());
		for (HttpField field : oldRequest.getHeaders()) {
            HttpHeader header = field.getHeader();
            // We have a new URI, so skip the host header if present.
            if (HttpHeader.HOST == header)
                continue;

            // Remove expectation headers.
            if (HttpHeader.EXPECT == header)
                continue;

            // Remove cookies.
            if (HttpHeader.COOKIE == header)
                continue;

            // Remove authorization headers.
            if (HttpHeader.AUTHORIZATION == header ||
                    HttpHeader.PROXY_AUTHORIZATION == header)
                continue;

            String value = field.getValue();
            if (!newRequest.getHeaders().contains(header, value))
                newRequest.header(field.getName(), value);
        }
        return newRequest;
    }

	protected HttpRequest newHttpRequest(HttpConversation conversation, URI uri) {
        return new HttpRequest(this, conversation, uri);
    }

	public Destination getDestination(String scheme, String host, int port) {
        return destinationFor(scheme, host, port);
    }

	protected HttpDestination destinationFor(String scheme, String host, int port) {
        port = normalizePort(scheme, port);

        Origin origin = new Origin(scheme, host, port);
        HttpDestination destination = destinations.get(origin);
		if (destination == null) {
            destination = transport.newHttpDestination(origin);
            addManaged(destination);
            HttpDestination existing = destinations.putIfAbsent(origin, destination);
			if (existing != null) {
                removeBean(destination);
                destination = existing;
			} else {
                if (LOG.isDebugEnabled())
                    LOG.debug("Created {}", destination);
            }
        }
        return destination;
    }

	protected boolean removeDestination(HttpDestination destination) {
        removeBean(destination);
        return destinations.remove(destination.getOrigin()) != null;
    }

	public List<Destination> getDestinations() {
        return new ArrayList<Destination>(destinations.values());
    }

	protected void send(final HttpRequest request, List<Response.ResponseListener> listeners) {
        String scheme = request.getScheme().toLowerCase(Locale.ENGLISH);
        if (!HttpScheme.HTTP.is(scheme) && !HttpScheme.HTTPS.is(scheme))
            throw new IllegalArgumentException("Invalid protocol " + scheme);

        String host = request.getHost().toLowerCase(Locale.ENGLISH);
        HttpDestination destination = destinationFor(scheme, host, request.getPort());
        destination.send(request, listeners);
    }

    protected void newConnection(final HttpDestination destination, final Promise<Connection> promise)
    {
        Origin.Address address = destination.getConnectAddress();
        resolver.resolve(address.getHost(), address.getPort(), new Promise<List<InetSocketAddress>>()
        {
            @Override
            public void succeeded(List<InetSocketAddress> socketAddresses)
            {
                Map<String, Object> context = new HashMap<>();
                context.put(HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY, destination);
                connect(socketAddresses, 0, context);
            }

            @Override
            public void failed(Throwable x)
            {
                promise.failed(x);
            }

            private void connect(List<InetSocketAddress> socketAddresses, int index, Map<String, Object> context)
            {
                context.put(HttpClientTransport.HTTP_CONNECTION_PROMISE_CONTEXT_KEY, new Promise.Wrapper<Connection>(promise)
                {
                    @Override
                    public void succeeded(Connection result)
                    {
                        getPromise().succeeded(result);
                    }

                    @Override
                    public void failed(Throwable x)
                    {
                        int nextIndex = index + 1;
                        if (nextIndex == socketAddresses.size())
                            getPromise().failed(x);
                        else
                            connect(socketAddresses, nextIndex, context);
                    }
                });
                transport.connect(socketAddresses.get(index), context);
            }
        });
    }

    private HttpConversation newConversation()
    {
        return new HttpConversation();
    }

    public ProtocolHandlers getProtocolHandlers()
    {
        return handlers;
    }

    protected ProtocolHandler findProtocolHandler(Request request, Response response)
    {
        return handlers.find(request, response);
    }

    /**
     * @return the {@link ByteBufferPool} of this {@link HttpClient}
     */
    public ByteBufferPool getByteBufferPool()
    {
        return byteBufferPool;
    }

    /**
     * @param byteBufferPool the {@link ByteBufferPool} of this {@link HttpClient}
     */
    public void setByteBufferPool(ByteBufferPool byteBufferPool)
    {
        this.byteBufferPool = byteBufferPool;
    }

    /**
     * @return the max time, in milliseconds, a connection can take to connect to destinations
     */
    @ManagedAttribute("The timeout, in milliseconds, for connect() operations")
    public long getConnectTimeout()
    {
        return connectTimeout;
    }

    /**
     * @param connectTimeout the max time, in milliseconds, a connection can take to connect to destinations
     * @see java.net.Socket#connect(SocketAddress, int)
     */
    public void setConnectTimeout(long connectTimeout)
    {
        this.connectTimeout = connectTimeout;
    }

    /**
     * @return the timeout, in milliseconds, for the default {@link SocketAddressResolver} created at startup
     * @see #getSocketAddressResolver()
     */
    public long getAddressResolutionTimeout()
    {
        return addressResolutionTimeout;
    }

    /**
     * <p>Sets the socket address resolution timeout used by the default {@link SocketAddressResolver}
     * created by this {@link HttpClient} at startup.</p>
     * <p>For more fine tuned configuration of socket address resolution, see
     * {@link #setSocketAddressResolver(SocketAddressResolver)}.</p>
     *
     * @param addressResolutionTimeout the timeout, in milliseconds, for the default {@link SocketAddressResolver} created at startup
     * @see #setSocketAddressResolver(SocketAddressResolver)
     */
    public void setAddressResolutionTimeout(long addressResolutionTimeout)
    {
        this.addressResolutionTimeout = addressResolutionTimeout;
    }

    /**
     * @return the max time, in milliseconds, a connection can be idle (that is, without traffic of bytes in either direction)
     */
    @ManagedAttribute("The timeout, in milliseconds, to close idle connections")
    public long getIdleTimeout()
    {
        return idleTimeout;
    }

    /**
     * @param idleTimeout the max time, in milliseconds, a connection can be idle (that is, without traffic of bytes in either direction)
     */
    public void setIdleTimeout(long idleTimeout)
    {
        this.idleTimeout = idleTimeout;
    }

    /**
     * @return the address to bind socket channels to
     * @see #setBindAddress(SocketAddress)
     */
    public SocketAddress getBindAddress()
    {
        return bindAddress;
    }

    /**
     * @param bindAddress the address to bind socket channels to
     * @see #getBindAddress()
     * @see SocketChannel#bind(SocketAddress)
     */
    public void setBindAddress(SocketAddress bindAddress)
    {
        this.bindAddress = bindAddress;
    }

    /**
     * @return the "User-Agent" HTTP field of this {@link HttpClient}
     */
    public HttpField getUserAgentField()
    {
        return agentField;
    }

    /**
     * @param agent the "User-Agent" HTTP header string of this {@link HttpClient}
     */
    public void setUserAgentField(HttpField agent)
    {
        if (agent.getHeader() != HttpHeader.USER_AGENT)
            throw new IllegalArgumentException();
        this.agentField = agent;
    }

    /**
     * @return whether this {@link HttpClient} follows HTTP redirects
     * @see Request#isFollowRedirects()
     */
    @ManagedAttribute("Whether HTTP redirects are followed")
    public boolean isFollowRedirects()
    {
        return followRedirects;
    }

    /**
     * @param follow whether this {@link HttpClient} follows HTTP redirects
     * @see #setMaxRedirects(int)
     */
    public void setFollowRedirects(boolean follow)
    {
        this.followRedirects = follow;
    }

    /**
     * @return the {@link Executor} of this {@link HttpClient}
     */
    public Executor getExecutor()
    {
        return executor;
    }

    /**
     * @param executor the {@link Executor} of this {@link HttpClient}
     */
    public void setExecutor(Executor executor)
    {
        this.executor = executor;
    }

    /**
     * @return the {@link Scheduler} of this {@link HttpClient}
     */
    public Scheduler getScheduler()
    {
        return scheduler;
    }

    /**
     * @param scheduler the {@link Scheduler} of this {@link HttpClient}
     */
    public void setScheduler(Scheduler scheduler)
    {
        this.scheduler = scheduler;
    }

    /**
     * @return the {@link SocketAddressResolver} of this {@link HttpClient}
     */
    public SocketAddressResolver getSocketAddressResolver()
    {
        return resolver;
    }

	public void setSocketAddressResolver(SocketAddressResolver resolver) {
        this.resolver = resolver;
    }

    @ManagedAttribute("The max number of connections per each destination")
	public int getMaxConnectionsPerDestination() {
        return maxConnectionsPerDestination;
    }

	public void setMaxConnectionsPerDestination(int maxConnectionsPerDestination) {
        this.maxConnectionsPerDestination = maxConnectionsPerDestination;
    }

    @ManagedAttribute("The max number of requests queued per each destination")
	public int getMaxRequestsQueuedPerDestination() {
        return maxRequestsQueuedPerDestination;
    }

	public void setMaxRequestsQueuedPerDestination(int maxRequestsQueuedPerDestination) {
        this.maxRequestsQueuedPerDestination = maxRequestsQueuedPerDestination;
    }

    @ManagedAttribute("The request buffer size")
	public int getRequestBufferSize() {
        return requestBufferSize;
    }

	public void setRequestBufferSize(int requestBufferSize) {
        this.requestBufferSize = requestBufferSize;
    }

    @ManagedAttribute("The response buffer size")
	public int getResponseBufferSize() {
        return responseBufferSize;
    }

	public void setResponseBufferSize(int responseBufferSize) {
        this.responseBufferSize = responseBufferSize;
    }

	public int getMaxRedirects() {
        return maxRedirects;
    }

	public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    @ManagedAttribute(value = "Whether the TCP_NODELAY option is enabled", name = "tcpNoDelay")
	public boolean isTCPNoDelay() {
        return tcpNoDelay;
    }

	public void setTCPNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    @Deprecated
	public boolean isDispatchIO() {
        // TODO this did default to true, so usage needs to be evaluated.
        return false;
    }

    @Deprecated
	public void setDispatchIO(boolean dispatchIO) {
    }

    @ManagedAttribute("Whether request/response events must be strictly ordered")
	public boolean isStrictEventOrdering() {
        return strictEventOrdering;
    }

	public void setStrictEventOrdering(boolean strictEventOrdering) {
        this.strictEventOrdering = strictEventOrdering;
    }

    @ManagedAttribute("Whether idle destinations are removed")
	public boolean isRemoveIdleDestinations() {
        return removeIdleDestinations;
    }

	public void setRemoveIdleDestinations(boolean removeIdleDestinations) {
        this.removeIdleDestinations = removeIdleDestinations;
    }

    @ManagedAttribute("Whether the connect() operation is blocking")
	public boolean isConnectBlocking() {
        return connectBlocking;
    }

	public void setConnectBlocking(boolean connectBlocking) {
        this.connectBlocking = connectBlocking;
    }

	public ProxyConfiguration getProxyConfiguration() {
        return proxyConfig;
    }

	protected HttpField getAcceptEncodingField() {
        return encodingField;
    }

	protected String normalizeHost(String host) {
        if (host != null && host.matches("\\[.*\\]"))
            return host.substring(1, host.length() - 1);
        return host;
    }

	public static int normalizePort(String scheme, int port) {
        return port > 0 ? port : HttpScheme.HTTPS.is(scheme) ? 443 : 80;
    }

	public boolean isDefaultPort(String scheme, int port) {
        return HttpScheme.HTTPS.is(scheme) ? port == 443 : port == 80;
    }

	private class ContentDecoderFactorySet implements Set<ContentDecoder.Factory> {
        private final Set<ContentDecoder.Factory> set = new HashSet<>();

        @Override
		public boolean add(ContentDecoder.Factory e) {
            boolean result = set.add(e);
            invalidate();
            return result;
        }

        @Override
		public boolean addAll(Collection<? extends ContentDecoder.Factory> c) {
            boolean result = set.addAll(c);
            invalidate();
            return result;
        }

        @Override
		public boolean remove(Object o) {
            boolean result = set.remove(o);
            invalidate();
            return result;
        }

        @Override
		public boolean removeAll(Collection<?> c) {
            boolean result = set.removeAll(c);
            invalidate();
            return result;
        }

        @Override
		public boolean retainAll(Collection<?> c) {
            boolean result = set.retainAll(c);
            invalidate();
            return result;
        }

        @Override
		public void clear() {
            set.clear();
            invalidate();
        }

        @Override
		public int size() {
            return set.size();
        }

        @Override
		public boolean isEmpty() {
            return set.isEmpty();
        }

        @Override
		public boolean contains(Object o) {
            return set.contains(o);
        }

        @Override
		public boolean containsAll(Collection<?> c) {
            return set.containsAll(c);
        }

        @Override
		public Iterator<ContentDecoder.Factory> iterator() {
            final Iterator<ContentDecoder.Factory> iterator = set.iterator();
			return new Iterator<ContentDecoder.Factory>() {
                @Override
				public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
				public ContentDecoder.Factory next() {
                    return iterator.next();
                }

                @Override
				public void remove() {
                    iterator.remove();
                    invalidate();
                }
            };
        }

        @Override
		public Object[] toArray() {
            return set.toArray();
        }

        @Override
		public <T> T[] toArray(T[] a) {
            return set.toArray(a);
        }

		private void invalidate() {
			if (set.isEmpty()) {
                encodingField = null;
			} else {
                StringBuilder value = new StringBuilder();
				for (Iterator<ContentDecoder.Factory> iterator = set.iterator(); iterator.hasNext();) {
                    ContentDecoder.Factory decoderFactory = iterator.next();
                    value.append(decoderFactory.getEncoding());
					if (iterator.hasNext()) {
						value.append(",");
					}
                }
                encodingField = new HttpField(HttpHeader.ACCEPT_ENCODING, value.toString());
            }
        }
    }
}
