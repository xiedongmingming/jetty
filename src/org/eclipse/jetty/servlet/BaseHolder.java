package org.eclipse.jetty.servlet;

import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.UnavailableException;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class BaseHolder<T> extends AbstractLifeCycle implements Dumpable {// T表示HOLDER存放的数据类型

    private static final Logger LOG = Log.getLogger(BaseHolder.class);

	public enum Source {// 表示HOLDER中存放的方式
		EMBEDDED, JAVAX_API, DESCRIPTOR, ANNOTATION
	};
    
	final protected Source _source;// 表示存放数据类型的来源
	protected transient Class<? extends T> _class;// 对应与SERVLET类???
	protected String _className;//
    protected boolean _extInstance;

	protected ServletHandler _servletHandler;//
    
	// *******************************************************************
	protected BaseHolder(Source source) {
		_source = source;
    }
	// *******************************************************************

	public Source getSource() {
        return _source;
    }
	public void initialize() throws Exception {
		if (!isStarted()) {// 表示还未启动
			throw new IllegalStateException("not started: " + this);
		}
    }
	@SuppressWarnings("unchecked")
	@Override
	public void doStart() throws Exception {
		if (_class == null && (_className == null || _className.equals(""))) {
			throw new UnavailableException("no class in holder");
		}
		if (_class == null) {
			try {
				_class = Loader.loadClass(Holder.class, _className);
			} catch (Exception e) {
                LOG.warn(e);
                throw new UnavailableException(e.getMessage());
            }
        }
    }
	@Override
	public void doStop() throws Exception {
		if (!_extInstance) {
			_class = null;
		}
    }
	public String getClassName() {
        return _className;
    }
	public Class<? extends T> getHeldClass() {
        return _class;
    }
	public ServletHandler getServletHandler() {
        return _servletHandler;
    }
	public void setServletHandler(ServletHandler servletHandler) {
        _servletHandler = servletHandler;
    }
	public void setClassName(String className) {
        _className = className;
		_class = null;
    }
	public void setHeldClass(Class<? extends T> held) {
		_class = held;
		if (held != null) {
			_className = held.getName();
        }
    }
	protected void illegalStateIfContextStarted() {
		if (_servletHandler != null) {
			ServletContext context = _servletHandler.getServletContext();
			if ((context instanceof ContextHandler.Context) && ((ContextHandler.Context) context).getContextHandler().isStarted()) {
				throw new IllegalStateException("Started");
			}
        }
    }
	public boolean isInstance() {
        return _extInstance;
    }

	// **************************************************************************
	@Override
	public void dump(Appendable out, String indent) throws IOException {
		out.append(toString()).append(" - ").append(AbstractLifeCycle.getState(this)).append("\n");
    }
    @Override
	public String dump() {
        return ContainerLifeCycle.dump(this);
    }
}
