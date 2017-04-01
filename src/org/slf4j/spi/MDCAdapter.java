package org.slf4j.spi;

import java.util.Map;

public interface MDCAdapter {
    public void put(String key, String val);
    public String get(String key);
    public void remove(String key);
    public void clear();
    public Map<String, String> getCopyOfContextMap();
    public void setContextMap(Map<String, String> contextMap);
}
