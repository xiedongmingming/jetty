package org.slf4j.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * SubstituteLoggerFactory manages instances of {@link SubstituteLogger}.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author Chetan Mehrotra
 */
public class SubstituteLoggerFactory implements ILoggerFactory {

    final ConcurrentMap<String, SubstituteLogger> loggers = new ConcurrentHashMap<String, SubstituteLogger>();

    @Override
	public Logger getLogger(String name) {
        SubstituteLogger logger = loggers.get(name);
        if (logger == null) {
            logger = new SubstituteLogger(name);
            SubstituteLogger oldLogger = loggers.putIfAbsent(name, logger);
            if (oldLogger != null)
                logger = oldLogger;
        }
        return logger;
    }

    public List<String> getLoggerNames() {
        return new ArrayList<String>(loggers.keySet());
    }

    public List<SubstituteLogger> getLoggers() {
        return new ArrayList<SubstituteLogger>(loggers.values());
    }

    public void clear() {
        loggers.clear();
    }
}
