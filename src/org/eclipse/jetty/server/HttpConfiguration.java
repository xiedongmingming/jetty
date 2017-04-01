package org.eclipse.jetty.server;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.TreeTrie;
import org.eclipse.jetty.util.Trie;

public class HttpConfiguration {// 创建HTTP连接工厂时需要该配置类

	// 可以通过该配置来修改HTTP请求中的数据

    public static final String SERVER_VERSION = "Jetty(" + Jetty.VERSION + ")";

    private int _outputBufferSize = 32 * 1024;

    private int _outputAggregationSize = _outputBufferSize / 4;

	// *********************************************************************************************************************
	// 请求和响应头的大小
	private int _requestHeaderSize = 8 * 1024;//
    private int _responseHeaderSize = 8 * 1024;

	private int _headerCacheSize = 512;
	// *********************************************************************************************************************

	private int _securePort;
	private long _blockingTimeout = -1;
	private String _secureScheme = HttpScheme.HTTPS.asString();
	private boolean _sendServerVersion = true;// 是否发送服务的版本信息
	private boolean _sendXPoweredBy = false;
	private boolean _sendDateHeader = true;
	private boolean _delayDispatchUntilContent = true;
	private boolean _persistentConnectionsEnabled = true;


	// *********************************************************************************************************************
	//    httpConfig.addCustomizer(new HttpConfiguration.Customizer() {
	//
	//        @Override
	//        public void customize(Connector connector, HttpConfiguration config, Request request) {// 注意:请求为JETTYSERVER中的请求
	//
	//            System.out.println("定制中的连接器类型: " + connector.getClass().getName());
	//
	//            String xForwardedProtoVal = request.getHeader(HttpHeader.X_FORWARDED_PROTO.asString());// 表示请求中携带了域:X-Forwarded-Proto: https
	//
	//            if (xForwardedProtoVal != null && xForwardedProtoVal.equals("https")) {// 只支持HTTPS协议--该域的值
	//                request.setSecure(true);
	//            } else {
	//                request.setSecure(false);
	//            }
	//
	//        }
	//    });

	private final List<Customizer> _customizers = new CopyOnWriteArrayList<>();// 该配置保存的所有定制器
    
	public interface Customizer {// 定制器(通过为HTTPCONFIGURATION添加该匿名实现来达到HTTP请求的前置处理)
		// 第一个参数表示该配置作用的连接器
		// 第二个参数表示该配置本身
		// 第三个参数表示该配置作用的连接器对应接收到的请求
		public void customize(Connector connector, HttpConfiguration channelConfig, Request request);// 实现订制的函数接口
    }

	public void addCustomizer(Customizer customizer) {// 表示为该配置添加定制器
		_customizers.add(customizer);
	}

	public List<Customizer> getCustomizers() {
		return _customizers;
	}

	@SuppressWarnings("unchecked")
	public <T> T getCustomizer(Class<T> type) {// 获取指定类的定制器(子类或子接口都可以)
		for (Customizer c : _customizers) {
			if (type.isAssignableFrom(c.getClass())) {
				return (T) c;
			}
		}
		return null;
	}

	public void setCustomizers(List<Customizer> customizers) {
		_customizers.clear();
		_customizers.addAll(customizers);
	}
	// *********************************************************************************************************************
	public interface ConnectionFactory {// ????
        HttpConfiguration getHttpConfiguration();
    }

	// *********************************************************************************************************************
	// 构造器
	public HttpConfiguration() {// HTTP连接工厂采用该默认配置
        _formEncodedMethods.put(HttpMethod.POST.asString(), Boolean.TRUE);
        _formEncodedMethods.put(HttpMethod.PUT.asString(), Boolean.TRUE);
    }
    public HttpConfiguration(HttpConfiguration config) {

		_customizers.addAll(config._customizers);// 继承定制器

		for (String s : config._formEncodedMethods.keySet()) {// 继承表单编码方式
			_formEncodedMethods.put(s, Boolean.TRUE);
		}

        _outputBufferSize = config._outputBufferSize;
        _outputAggregationSize = config._outputAggregationSize;

        _requestHeaderSize = config._requestHeaderSize;
        _responseHeaderSize = config._responseHeaderSize;

        _headerCacheSize = config._headerCacheSize;

        _secureScheme = config._secureScheme;
        _securePort = config._securePort;
        _blockingTimeout = config._blockingTimeout;
        _sendDateHeader = config._sendDateHeader;
        _sendServerVersion = config._sendServerVersion;
        _sendXPoweredBy = config._sendXPoweredBy;
        _delayDispatchUntilContent = config._delayDispatchUntilContent;
        _persistentConnectionsEnabled = config._persistentConnectionsEnabled;
        _maxErrorDispatches = config._maxErrorDispatches;
    }
	// *********************************************************************************************************************
	private final Trie<Boolean> _formEncodedMethods = new TreeTrie<>();// 树形容器--表示支持的表单提交方法(默认为POST和PUT)

	public void setFormEncodedMethods(String... methods) {
		_formEncodedMethods.clear();
		for (String method : methods) {
			addFormEncodedMethod(method);
		}
	}
	public Set<String> getFormEncodedMethods() {
		return _formEncodedMethods.keySet();
	}
	public void addFormEncodedMethod(String method) {
		_formEncodedMethods.put(method, Boolean.TRUE);
	}
	public boolean isFormEncodedMethod(String method) {
		return Boolean.TRUE.equals(_formEncodedMethods.get(method));
	}
	// *********************************************************************************************************************
    public int getOutputBufferSize() {
        return _outputBufferSize;
    }
	public void setOutputBufferSize(int outputBufferSize) {
		_outputBufferSize = outputBufferSize;
		setOutputAggregationSize(outputBufferSize / 4);
	}

	public int getOutputAggregationSize() {
		return _outputAggregationSize;
	}
	public void setOutputAggregationSize(int outputAggregationSize) {
		_outputAggregationSize = outputAggregationSize;
	}

	// *********************************************************************************************************************
    public int getRequestHeaderSize() {
        return _requestHeaderSize;
    }
	public void setRequestHeaderSize(int requestHeaderSize) {
		_requestHeaderSize = requestHeaderSize;
	}
    public int getResponseHeaderSize() {
        return _responseHeaderSize;
    }
	public void setResponseHeaderSize(int responseHeaderSize) {
		_responseHeaderSize = responseHeaderSize;
	}
    public int getHeaderCacheSize() {
        return _headerCacheSize;
    }
	public void setHeaderCacheSize(int headerCacheSize) {
		_headerCacheSize = headerCacheSize;
	}
    public int getSecurePort() {
        return _securePort;
    }
	public void setSecurePort(int securePort) {
		_securePort = securePort;
	}
    public String getSecureScheme() {
        return _secureScheme;
    }
	public void setSecureScheme(String secureScheme) {
		_secureScheme = secureScheme;
	}

	public void setPersistentConnectionsEnabled(boolean persistentConnectionsEnabled) {
		_persistentConnectionsEnabled = persistentConnectionsEnabled;
	}
    public boolean isPersistentConnectionsEnabled() {
        return _persistentConnectionsEnabled;
    }
    public long getBlockingTimeout() {
        return _blockingTimeout;
    }
    public void setBlockingTimeout(long blockingTimeout) {
        _blockingTimeout = blockingTimeout;
    }
	public void setSendServerVersion(boolean sendServerVersion) {// 设置是否发送服务版本信息
        _sendServerVersion = sendServerVersion;
    }
    public boolean getSendServerVersion() {
        return _sendServerVersion;
    }

    public void setSendXPoweredBy (boolean sendXPoweredBy) {
        _sendXPoweredBy = sendXPoweredBy;
    }
    public boolean getSendXPoweredBy() {
        return _sendXPoweredBy;
    }
    public void setSendDateHeader(boolean sendDateHeader) {
        _sendDateHeader = sendDateHeader;
    }
    public boolean getSendDateHeader() {
        return _sendDateHeader;
    }
    public void setDelayDispatchUntilContent(boolean delay) {
        _delayDispatchUntilContent = delay;
    }
    public boolean isDelayDispatchUntilContent() {
        return _delayDispatchUntilContent;
    }

	private int _maxErrorDispatches = 10;

	public int getMaxErrorDispatches() {
		return _maxErrorDispatches;
	}

	public void setMaxErrorDispatches(int max) {
		_maxErrorDispatches = max;
	}
	// ************************************************************************************
	public void writePoweredBy(Appendable out, String preamble, String postamble) throws IOException {
		if (getSendServerVersion()) {
			if (preamble != null) {
				out.append(preamble);
			}
			out.append(Jetty.POWERED_BY);
			if (postamble != null) {
				out.append(postamble);
			}
		}
	}

	// ************************************************************************************
	@Override
	public String toString() {
		return String.format("%s@%x{%d/%d,%d/%d,%s://:%d,%s}", this.getClass().getSimpleName(), hashCode(),
				_outputBufferSize, _outputAggregationSize, _requestHeaderSize, _responseHeaderSize, _secureScheme,
				_securePort, _customizers);
	}

	// ************************************************************************************

	// ************************************************************************************
}
