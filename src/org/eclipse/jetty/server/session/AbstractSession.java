package org.eclipse.jetty.server.session;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;

import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.util.log.Logger;

public abstract class AbstractSession implements AbstractSessionManager.SessionIf {

	final static Logger LOG = SessionHandler.LOG;

    public final static String SESSION_CREATED_SECURE="org.eclipse.jetty.security.sessionCreatedSecure";
    private  String _clusterId; // ID without any node (ie "worker") id appended
    private  String _nodeId;    // ID of session with node(ie "worker") id appended
    private final AbstractSessionManager _manager;
    private boolean _idChanged;
    private final long _created;
    private long _cookieSet;
    private long _accessed;         // the time of the last access
    private long _lastAccessed;     // the time of the last access excluding this one
    private boolean _invalid;
    private boolean _doInvalidate;
    private long _maxIdleMs;
    private boolean _newSession;
    private int _requests;

	protected AbstractSession(AbstractSessionManager abstractSessionManager, HttpServletRequest request) {

        _manager = abstractSessionManager;

		_newSession = true;
		_created = System.currentTimeMillis();
		_clusterId = _manager._sessionIdManager.newSessionId(request, _created);
		_nodeId = _manager._sessionIdManager.getNodeId(_clusterId, request);
		_accessed = _created;
		_lastAccessed = _created;
		_requests = 1;
		_maxIdleMs = _manager._dftMaxIdleSecs > 0 ? _manager._dftMaxIdleSecs * 1000L : -1;

    }

	protected AbstractSession(AbstractSessionManager abstractSessionManager, long created, long accessed,
			String clusterId) {
        _manager = abstractSessionManager;
		_created = created;
		_clusterId = clusterId;
		_nodeId = _manager._sessionIdManager.getNodeId(_clusterId, null);
		_accessed = accessed;
		_lastAccessed = accessed;
		_requests = 1;
		_maxIdleMs = _manager._dftMaxIdleSecs > 0 ? _manager._dftMaxIdleSecs * 1000L : -1;
    }

	protected void checkValid() throws IllegalStateException {
		if (_invalid) {
			throw new IllegalStateException("id=" + _clusterId + " created=" + _created + " accessed=" + _accessed
					+ " lastaccessed=" + _lastAccessed + " maxInactiveMs=" + _maxIdleMs);
		}
    }

	protected boolean checkExpiry(long time) {
		if (_maxIdleMs > 0 && _lastAccessed > 0 && _lastAccessed + _maxIdleMs < time) {
			return true;
		}
        return false;
    }
    @Override
	public AbstractSession getSession() {
        return this;
    }

	public long getAccessed() {
		synchronized (this) {
            return _accessed;
        }
    }

    public abstract Map<String,Object> getAttributeMap();
    public abstract int getAttributes();

	public abstract Set<String> getNames();
 
	public long getCookieSetTime() {
        return _cookieSet;
    }

	public void setCookieSetTime(long time) {
        _cookieSet = time;
    }
    @Override
	public long getCreationTime() throws IllegalStateException {
        checkValid();
        return _created;
    }
    @Override
	public String getId() throws IllegalStateException {
        return _manager._nodeIdInSessionId?_nodeId:_clusterId;
    }

	public String getNodeId() {
        return _nodeId;
    }

	public String getClusterId() {
        return _clusterId;
    }
    @Override
	public long getLastAccessedTime() throws IllegalStateException {
        checkValid();
        return _lastAccessed;
    }

	public void setLastAccessedTime(long time) {
        _lastAccessed = time;
    }
    @Override
	public int getMaxInactiveInterval() {
        return (int)(_maxIdleMs/1000);
    }
    @Override
	public ServletContext getServletContext() {
        return _manager._context;
    }
    @Deprecated
    @Override
	public HttpSessionContext getSessionContext() throws IllegalStateException {
        checkValid();
        return AbstractSessionManager.__nullSessionContext;
    }
    @Deprecated
    @Override
	public Object getValue(String name) throws IllegalStateException {
        return getAttribute(name);
    }

	public void renewId(HttpServletRequest request) {
        _manager._sessionIdManager.renewSessionId(getClusterId(), getNodeId(), request); 
        setIdChanged(true);
    }

	public SessionManager getSessionManager() {
        return _manager;
    }

	protected void setClusterId(String clusterId) {
        _clusterId = clusterId;
    }

	protected void setNodeId(String nodeId) {
        _nodeId = nodeId;
    }
	protected boolean access(long time) {
		synchronized (this) {
			if (_invalid) {
				return false;
			}
			_newSession = false;
			_lastAccessed = _accessed;
			_accessed = time;
			if (checkExpiry(time)) {
                invalidate();
                return false;
            }
            _requests++;
            return true;
        }
    }
	protected void complete() {
		synchronized (this) {
            _requests--;
			if (_doInvalidate && _requests <= 0) {
				doInvalidate();
			}
        }
    }
	protected void timeout() throws IllegalStateException {
        _manager.removeSession(this,true);
        boolean do_invalidate=false;
		synchronized (this) {
			if (!_invalid) {
				if (_requests <= 0) {
					do_invalidate = true;
				} else {
					_doInvalidate = true;
				}
            }
        }
		if (do_invalidate) {
			doInvalidate();
		}
    }
    @Override
	public void invalidate() throws IllegalStateException {
        checkValid();
        _manager.removeSession(this,true);
        doInvalidate();
    }

	protected void doInvalidate() throws IllegalStateException {
		try {
			if (isValid()) {
				clearAttributes();
			}
		} finally {
			synchronized (this) {
				_invalid = true;
            }
        }
    }
    public abstract void clearAttributes();

	public boolean isIdChanged() {
        return _idChanged;
    }
    @Override
	public boolean isNew() throws IllegalStateException {
        checkValid();
        return _newSession;
    }
    @Deprecated
    @Override
	public void putValue(java.lang.String name, java.lang.Object value) throws IllegalStateException {
		changeAttribute(name, value);
    }
    @Override
	public void removeAttribute(String name) {
        setAttribute(name,null);
    }
    @Deprecated
    @Override
	public void removeValue(java.lang.String name) throws IllegalStateException {
        removeAttribute(name);
    }
    @Override
	public Enumeration<String> getAttributeNames() {
		synchronized (this) {
            checkValid();
            return doGetAttributeNames();
        }
    }

    @Deprecated
    @Override
	public String[] getValueNames() throws IllegalStateException {
		synchronized (this) {
            checkValid();
            Enumeration<String> anames = doGetAttributeNames();
			if (anames == null) {
				return new String[0];
			}
            ArrayList<String> names = new ArrayList<String>();
			while (anames.hasMoreElements()) {
				names.add(anames.nextElement());
			}
            return names.toArray(new String[names.size()]);
        }
    }

	public abstract Object doPutOrRemove(String name, Object value);
    public abstract Object doGet(String name);
    public abstract Enumeration<String> doGetAttributeNames();

    @Override
	public Object getAttribute(String name) {
		synchronized (this) {
            checkValid();
            return doGet(name);
        }
    }
    @Override
	public void setAttribute(String name, Object value) {
        changeAttribute(name,value);
    }
    @Deprecated
	protected boolean updateAttribute(String name, Object value) {
        Object old=null;
		synchronized (this) {
            checkValid();
			old = doPutOrRemove(name, value);
        }

		if (value == null || !value.equals(old)) {
			if (old != null) {
				unbindValue(name, old);
			}
			if (value != null) {
				bindValue(name, value);
			}
			_manager.doSessionAttributeListeners(this, name, old, value);
            return true;
        }
        return false;
    }

	protected Object changeAttribute(String name, Object value) {
        Object old=null;
		synchronized (this) {
            checkValid();
			old = doPutOrRemove(name, value);
        }

        callSessionAttributeListeners(name, value, old);

        return old;
    }

	protected void callSessionAttributeListeners(String name, Object newValue, Object oldValue) {
		if (newValue == null || !newValue.equals(oldValue)) {
			if (oldValue != null) {
				unbindValue(name, oldValue);
			}
			if (newValue != null) {
				bindValue(name, newValue);
			}
			_manager.doSessionAttributeListeners(this, name, oldValue, newValue);
        }
    }

	public void setIdChanged(boolean changed) {
		_idChanged = changed;
    }

    @Override
	public void setMaxInactiveInterval(int secs) {
		_maxIdleMs = (long) secs * 1000L;
    }

    @Override
	public String toString() {
        return this.getClass().getName()+":"+getId()+"@"+hashCode();
    }

	public void bindValue(java.lang.String name, Object value) {
		if (value != null && value instanceof HttpSessionBindingListener) {
			((HttpSessionBindingListener) value).valueBound(new HttpSessionBindingEvent(this, name));
		}
    }

	public boolean isValid() {
        return !_invalid;
    }

	protected void cookieSet() {
		synchronized (this) {
			_cookieSet = _accessed;
        }
    }

	public int getRequests() {
		synchronized (this) {
            return _requests;
        }
    }

	public void setRequests(int requests) {
		synchronized (this) {
            _requests=requests;
        }
    }

	public void unbindValue(java.lang.String name, Object value) {
		if (value != null && value instanceof HttpSessionBindingListener) {
			((HttpSessionBindingListener) value).valueUnbound(new HttpSessionBindingEvent(this, name));
		}
    }
	public void willPassivate() {
		synchronized (this) {
            HttpSessionEvent event = new HttpSessionEvent(this);
			for (Iterator<Object> iter = getAttributeMap().values().iterator(); iter.hasNext();) {
                Object value = iter.next();
				if (value instanceof HttpSessionActivationListener) {
                    HttpSessionActivationListener listener = (HttpSessionActivationListener) value;
                    listener.sessionWillPassivate(event);
                }
            }
        }
    }
	public void didActivate() {
		synchronized (this) {
            HttpSessionEvent event = new HttpSessionEvent(this);
			for (Iterator<Object> iter = getAttributeMap().values().iterator(); iter.hasNext();) {
                Object value = iter.next();
				if (value instanceof HttpSessionActivationListener) {
                    HttpSessionActivationListener listener = (HttpSessionActivationListener) value;
                    listener.sessionDidActivate(event);
                }
            }
        }
    }
}
