package org.eclipse.jetty.server;


import java.util.List;

import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;

public interface ConnectionFactory {// 接口(三个接口函数)

	// 最大的作用就是生成连接

    public String getProtocol();
    public List<String> getProtocols();

	public Connection newConnection(Connector connector, EndPoint endPoint);// 生成一个连接

	public interface Upgrading extends ConnectionFactory {// 提供了新的接口函数(共四个接口函数)
        public Connection upgradeConnection(Connector connector, EndPoint endPoint, MetaData.Request upgradeRequest,HttpFields responseFields) throws BadMessageException;
    }
}
