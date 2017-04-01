package org.eclipse.jetty.util.component;

import java.io.FileWriter;
import java.io.Writer;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class FileNoticeLifeCycleListener implements LifeCycle.Listener {// 用于将监听器事件记录到文件中

    private static final Logger LOG = Log.getLogger(FileNoticeLifeCycleListener.class);
    
	private final String _filename;// 日志文件名
    
	public FileNoticeLifeCycleListener(String filename) {
		_filename = filename;
    }

	private void writeState(String action, LifeCycle lifecycle) {// 底层实现
		try (Writer out = new FileWriter(_filename, true)) {
            out.append(action).append(" ").append(lifecycle.toString()).append("\n");
		} catch (Exception e) {
            LOG.warn(e);
        }
    }

	// ***************************************************************
	public void lifeCycleStarting(LifeCycle event) {
		writeState("STARTING", event);
    }
	public void lifeCycleStarted(LifeCycle event) {
		writeState("STARTED", event);
    }
	public void lifeCycleFailure(LifeCycle event, Throwable cause) {
		writeState("FAILED", event);
    }
	public void lifeCycleStopping(LifeCycle event) {
		writeState("STOPPING", event);
    }
	public void lifeCycleStopped(LifeCycle event) {
		writeState("STOPPED", event);
    }
	// ***************************************************************
}
