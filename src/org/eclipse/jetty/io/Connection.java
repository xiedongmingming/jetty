package org.eclipse.jetty.io;

import java.io.Closeable;
import java.nio.ByteBuffer;

public interface Connection extends Closeable {// 共12个接口函数(有一些监听函数)

	// 表示一个HTTP网络连接(每个网络连接都会关联一个端点)
	// *******************************************************
	public void addListener(Listener listener);
	public void removeListener(Listener listener);

	public void onOpen();// 回调
	public void onClose();// 回调

	public boolean onIdleExpired();// 表示空闲超时回调函数--主要需要一个返回值

	public EndPoint getEndPoint();// 对应底层的端点

	public int getMessagesIn();// 用于获取数据的输入与输出
    public int getMessagesOut();

    public long getBytesIn();
    public long getBytesOut();

	public long getCreatedTimeStamp();// 表示该连接创建的时间

	@Override
	public void close();// 继承的接口

	// *******************************************************
	public interface UpgradeFrom extends Connection {// 用于进行网络连接的更新
		ByteBuffer onUpgradeFrom();// 返回更新之前的缓存
    }
    public interface UpgradeTo extends Connection {
		void onUpgradeTo(ByteBuffer prefilled);// 将参数中携带的数据更新到当前连接上
    }

	// *******************************************************
	public interface Listener {// 监听器
        public void onOpened(Connection connection);
        public void onClosed(Connection connection);
        public static class Adapter implements Listener {
            @Override
            public void onOpened(Connection connection) {

            }
            @Override
            public void onClosed(Connection connection) {

            }
        }
    }
	// *******************************************************
}
