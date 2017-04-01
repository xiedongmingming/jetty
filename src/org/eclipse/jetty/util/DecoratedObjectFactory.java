package org.eclipse.jetty.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class DecoratedObjectFactory implements Iterable<Decorator> {// 装饰工厂

    private static final Logger LOG = Log.getLogger(DecoratedObjectFactory.class);

    public static final String ATTR = DecoratedObjectFactory.class.getName();

	private List<Decorator> decorators = new ArrayList<>();// 包含所有的装饰器

	// **************************************************************************
	public void addDecorator(Decorator decorator) {// 添加装饰器
        this.decorators.add(decorator);
    }
	public void clear() {
        this.decorators.clear();
    }

	public <T> T createInstance(Class<T> clazz) throws InstantiationException, IllegalAccessException {
		if (LOG.isDebugEnabled()) {
            LOG.debug("Creating Instance: " + clazz);
        }
        T o = clazz.newInstance();
        return decorate(o);
    }

	public <T> T decorate(T obj) {
        T f = obj;
		for (int i = decorators.size() - 1; i >= 0; i--) {
            f = decorators.get(i).decorate(f);
        }
        return f;
    }

	public void destroy(Object obj) {
		for (Decorator decorator : this.decorators) {
            decorator.destroy(obj);
        }
    }
	public List<Decorator> getDecorators() {
		return Collections.unmodifiableList(decorators);
    }
	public void setDecorators(List<? extends Decorator> decorators) {
        this.decorators.clear();
		if (decorators != null) {
            this.decorators.addAll(decorators);
        }
    }

	// ******************************************************************************************
    @Override
	public Iterator<Decorator> iterator() {
		return this.decorators.iterator();
	}
	@Override
	public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(this.getClass().getName()).append("[decorators=");
        str.append(Integer.toString(decorators.size()));
        str.append("]");
        return str.toString();
    }
	// ******************************************************************************************
}
