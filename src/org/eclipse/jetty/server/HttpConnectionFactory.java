package org.eclipse.jetty.server;

import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.annotation.Name;

public class HttpConnectionFactory extends AbstractConnectionFactory implements HttpConfiguration.ConnectionFactory {

	// 实现类列表:

	private final HttpConfiguration _config;// 对应的HTTP配置(必须有)

	private HttpCompliance _httpCompliance;// 兼容相关默认为:HttpCompliance.RFC7230

    private boolean _recordHttpComplianceViolations = false;

	// ****************************************************************************************************************
	// 构造函数
	public HttpConnectionFactory() {
        this(new HttpConfiguration());
    }
	public HttpConnectionFactory(@Name("config") HttpConfiguration config) {
		this(config, null);
    }
	public HttpConnectionFactory(@Name("config") HttpConfiguration config, @Name("compliance") HttpCompliance compliance) {

		super(HttpVersion.HTTP_1_1.asString());

		_config = config;

		_httpCompliance = compliance == null ? HttpCompliance.RFC7230 : compliance;

		if (config == null) {
			throw new IllegalArgumentException("null httpconfiguration");
		}

		addBean(_config);// 作为一种BEAN来管理配置
    }
	// ****************************************************************************************************************
	public HttpCompliance getHttpCompliance() {
        return _httpCompliance;
    }
	public void setHttpCompliance(HttpCompliance httpCompliance) {
		_httpCompliance = httpCompliance;
	}
	public boolean isRecordHttpComplianceViolations() {
        return _recordHttpComplianceViolations;
    }
	public void setRecordHttpComplianceViolations(boolean recordHttpComplianceViolations) {
		this._recordHttpComplianceViolations = recordHttpComplianceViolations;
	}
	// **********************************************************************************
	// 接口实现函数
	@Override
	public HttpConfiguration getHttpConfiguration() {
		return _config;
	}
	// ****************************************************************************************************************
    @Override
	public Connection newConnection(Connector connector, EndPoint endPoint) {

		// 第一个参数为服务器连接器
		// 第二个参数为生成的端点

        HttpConnection conn = new HttpConnection(_config, connector, endPoint, _httpCompliance, isRecordHttpComplianceViolations());

		return configure(conn, connector, endPoint);// 父类函数(用于为生成的连接进行进一步的配置)
    }
	// ****************************************************************************************************************
}
