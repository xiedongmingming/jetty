package org.eclipse.jetty.server.handler;

import java.io.IOException;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class AbstractHandler extends ContainerLifeCycle implements Handler {// 将HANDLER与生命周期容器关联起来
	
    private static final Logger LOG = Log.getLogger(AbstractHandler.class);

	private Server _server;// 表示该HANDLER所属的服务

	// ******************************************************************************************
	public AbstractHandler() {

    }
	// ******************************************************************************************
	
    @Override
	protected void doStart() throws Exception {
        if (_server == null) {
			LOG.warn("no server set for {}", this);
		}
		super.doStart();// 调用容器中的对应函数
    }
    @Override
    protected void doStop() throws Exception {
		super.doStop();// 调用容器中的对应函数
    }

	// ****************************************************************
	// HANDLER的接口函数(少一个): Handler
    @Override
    public void setServer(Server server) {
		if (_server == server) {
			return;
		}
		if (isStarted()) {// 表示已经启动
			throw new IllegalStateException(STARTED);
		}
        _server = server;
    }
    @Override
    public Server getServer() {
        return _server;
    }
    @Override
    public void destroy() {
		if (!isStopped()) {// 必须停止后才能销毁
			throw new IllegalStateException("!STOPPED");
		}
        super.destroy();
    }
	// ****************************************************************

    @Override
    public void dumpThis(Appendable out) throws IOException {
        out.append(toString()).append(" - ").append(getState()).append('\n');
    }
}
