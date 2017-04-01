package org.eclipse.jetty.util;

public interface Decorator {// 装饰器
    <T> T decorate(T o);
    void destroy(Object o);
}
