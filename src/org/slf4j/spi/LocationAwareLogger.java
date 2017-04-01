package org.slf4j.spi;

import org.slf4j.Logger;
import org.slf4j.Marker;

public interface LocationAwareLogger extends Logger {

    final public int TRACE_INT = 00;
    final public int DEBUG_INT = 10;
    final public int INFO_INT = 20;
    final public int WARN_INT = 30;
    final public int ERROR_INT = 40;

    public void log(Marker marker, String fqcn, int level, String message, Object[] argArray, Throwable t);
}
