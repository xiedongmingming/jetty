package org.eclipse.jetty.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class AttributesMap implements Attributes {
	
	//a map providing thread safety and atomicity guarantees. 
    private final AtomicReference<ConcurrentMap<String, Object>> _map = new AtomicReference<>();//底层实现
	//**************************************************************************************
    public AttributesMap() {
    }
    public AttributesMap(AttributesMap attributes) {
        ConcurrentMap<String, Object> map = attributes.map();
        if (map != null) {
            _map.set(new ConcurrentHashMap<>(map));
		}
    }
	//**************************************************************************************
    private ConcurrentMap<String, Object> map() {
        return _map.get();
    }
    private ConcurrentMap<String, Object> ensureMap() {//若为空
        while (true) {
            ConcurrentMap<String, Object> map = map();
            if (map != null) {
                return map;
			}
            map = new ConcurrentHashMap<>();
            if (_map.compareAndSet(null, map)) {
                return map;
			}
        }
    }
	//**************************************************************************************
	//下面是实现的接口
    @Override
    public void removeAttribute(String name) {
        Map<String, Object> map = map();
        if (map != null) {
            map.remove(name);
		}
    }
    @Override
    public void setAttribute(String name, Object attribute) {
        if (attribute == null) {
            removeAttribute(name);
		} else {
            ensureMap().put(name, attribute);
		}
    }
    @Override
    public Object getAttribute(String name) {
        Map<String, Object> map = map();
        return map == null ? null : map.get(name);
    }
    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(getAttributeNameSet());
    }
	@Override
    public void clearAttributes() {
        Map<String, Object> map = map();
        if (map != null) {
            map.clear();
		}
    }
	//**************************************************************************************
    public Set<String> getAttributeNameSet() {
        return keySet();
    }
    public Set<Map.Entry<String, Object>> getAttributeEntrySet() {
        Map<String, Object> map = map();
        return map == null ? Collections.<Map.Entry<String, Object>>emptySet() : map.entrySet();
    }
    public static Enumeration<String> getAttributeNamesCopy(Attributes attrs) {//也是引用
        if (attrs instanceof AttributesMap) {//
            return Collections.enumeration(((AttributesMap)attrs).keySet());
		}
        List<String> names = new ArrayList<>();
        names.addAll(Collections.list(attrs.getAttributeNames()));
        return Collections.enumeration(names);
    }
    public int size() {
        Map<String, Object> map = map();
        return map == null ? 0 : map.size();
    }
    private Set<String> keySet() {//
        Map<String, Object> map = map();
        return map == null ? Collections.<String> emptySet() : map.keySet();//语法: Collections.<String>
    }
    public void addAll(Attributes attributes) {//
        Enumeration<String> e = attributes.getAttributeNames();
        while (e.hasMoreElements()) {
            String name = e.nextElement();
            setAttribute(name, attributes.getAttribute(name));
        }
    }
	//**************************************************************************************
    @Override
    public String toString() {
        Map<String, Object> map = map();
        return map == null ? "{}" : map.toString();
    }
}
