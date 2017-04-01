package org.eclipse.jetty.util.component;

import java.util.Collection;

public interface Container {// 容器(包含BEAN)

	// ***********************************************************
	// 容器中BEAN的管理
	public boolean addBean(Object o);// 添加BEAN
	public Collection<Object> getBeans();// 获取容器中包含的所有BEAN
	public <T> Collection<T> getBeans(Class<T> clazz);// 获取指定类型的BEAN
	public <T> T getBean(Class<T> clazz);// 获取指定类型的一个BEAN(注意同上面的区别)
	public boolean removeBean(Object o);// 删除一个BEAN

	// ***********************************************************
	// 容器的监听器
    public void addEventListener(Listener listener);
    public void removeEventListener(Listener listener);
	// ***********************************************************

	public interface Listener {// 
		void beanAdded(Container parent, Object child);// 第一个参数表示所在的顶层容器
        void beanRemoved(Container parent, Object child);
    }

	public interface InheritedListener extends Listener {// 表示被继承的监听器
		// 表示顶层容器的监听器会被该容器中包含的容器继承(通过BEAN的方式)
    }
}
