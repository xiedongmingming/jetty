package org.eclipse.jetty.util.component;

import java.io.FileWriter;
import java.io.Writer;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class FileNoticeLifeCycleListener implements LifeCycle.Listener {// ���ڽ��������¼���¼���ļ���

    private static final Logger LOG = Log.getLogger(FileNoticeLifeCycleListener.class);
    
	private final String _filename;// ��־�ļ���
    
	public FileNoticeLifeCycleListener(String filename) {
		_filename = filename;
    }

	private void writeState(String action, LifeCycle lifecycle) {// �ײ�ʵ��
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
