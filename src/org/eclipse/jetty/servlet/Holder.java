package org.eclipse.jetty.servlet;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.Registration;
import javax.servlet.ServletContext;

import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class Holder<T> extends BaseHolder<T> {// T表示HOLDER中存放的数据类型

    private static final Logger LOG = Log.getLogger(Holder.class);

	protected final Map<String, String> _initParams = new HashMap<String, String>(3);// ???

    protected String _displayName;
	protected String _name;// 对应SERVLET的名字

	protected boolean _asyncSupported;// 是否支持异步方式

	// **************************************************************************
	protected Holder(Source source) {// 参数表示存放数据类型的来源
        super(source);
		switch (_source) {
            case JAVAX_API:
            case DESCRIPTOR:
            case ANNOTATION:
            	_asyncSupported = false;
                break;
            default:
            	_asyncSupported = true;
        }
    }
	// **************************************************************************
	public String getDisplayName() {
        return _displayName;
    }
	public String getInitParameter(String param) {// 用于获取SERVLET的配置参数
		if (_initParams == null) {
			return null;
		}
        return _initParams.get(param);
    }
	@SuppressWarnings("unchecked")
	public Enumeration<String> getInitParameterNames() {
		if (_initParams == null) {
			return Collections.enumeration(Collections.EMPTY_LIST);
		}
        return Collections.enumeration(_initParams.keySet());
    }
	public Map<String, String> getInitParameters() {
        return _initParams;
    }
	public String getName() {
        return _name;
    }
	public void destroyInstance(Object instance) throws Exception {
    }
    @Override
	public void setClassName(String className) {
        super.setClassName(className);
		if (_name == null) {
			_name = className + "-" + Integer.toHexString(this.hashCode());
		}
    }
    @Override
	public void setHeldClass(Class<? extends T> held) {
        super.setHeldClass(held);
		if (held != null) {
			if (_name == null) {
				_name = held.getName() + "-" + Integer.toHexString(this.hashCode());
			}
        }
    }
	public void setDisplayName(String name) {
        _displayName=name;
    }
	public void setInitParameter(String param, String value) {
        _initParams.put(param,value);
    }
	public void setInitParameters(Map<String, String> map) {
        _initParams.clear();
        _initParams.putAll(map);
    }
	public void setName(String name) {
        _name = name;
    }
	public void setAsyncSupported(boolean suspendable) {
		_asyncSupported = suspendable;
    }
	public boolean isAsyncSupported() {
        return _asyncSupported;
    }
    @Override
	public void dump(Appendable out, String indent) throws IOException {
        super.dump(out,indent);
		ContainerLifeCycle.dump(out, indent, _initParams.entrySet());
    }
    @Override
	public String dump() {
        return super.dump();
    }
    @Override
	public String toString() {
		return String.format("%s@%x==%s", _name, hashCode(), _className);
    }
	protected class HolderConfig {
		public ServletContext getServletContext() {
            return _servletHandler.getServletContext();
        }
		public String getInitParameter(String param) {
            return Holder.this.getInitParameter(param);
        }
		public Enumeration<String> getInitParameterNames() {
            return Holder.this.getInitParameterNames();
        }
    }
	protected class HolderRegistration implements Registration.Dynamic {
        @Override
		public void setAsyncSupported(boolean isAsyncSupported) {
            illegalStateIfContextStarted();
            Holder.this.setAsyncSupported(isAsyncSupported);
        }
		public void setDescription(String description) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(this + " is " + description);
			}
        }
        @Override
		public String getClassName() {
            return Holder.this.getClassName();
        }
        @Override
		public String getInitParameter(String name) {
            return Holder.this.getInitParameter(name);
        }
        @Override
		public Map<String, String> getInitParameters() {
            return Holder.this.getInitParameters();
        }
        @Override
		public String getName() {
            return Holder.this.getName();
        }
        @Override
		public boolean setInitParameter(String name, String value) {
            illegalStateIfContextStarted();
            if (name == null) {
                throw new IllegalArgumentException("init parameter name required");
            }
            if (value == null) {
                throw new IllegalArgumentException("non-null value required for init parameter " + name);
            }
			if (Holder.this.getInitParameter(name) != null) {
				return false;
			}
            Holder.this.setInitParameter(name,value);
            return true;
        }
        @Override
		public Set<String> setInitParameters(Map<String, String> initParameters) {
            illegalStateIfContextStarted();
			Set<String> clash = null;
			for (Map.Entry<String, String> entry : initParameters.entrySet()) {
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException("init parameter name required");
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException("non-null value required for init parameter " + entry.getKey());
                }
				if (Holder.this.getInitParameter(entry.getKey()) != null) {
					if (clash == null) {
						clash = new HashSet<String>();
					}
                    clash.add(entry.getKey());
                }
            }
			if (clash != null) {
				return clash;
			}
            Holder.this.getInitParameters().putAll(initParameters);
            return Collections.emptySet();
        }
    }
}
