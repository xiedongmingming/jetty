package org.eclipse.jetty.util;

import java.util.Enumeration;

public interface Attributes {//五个接口函数
    public void removeAttribute(String name);
    public void setAttribute(String name, Object attribute);
    public Object getAttribute(String name);
    public Enumeration<String> getAttributeNames();
    public void clearAttributes();
}
