package org.eclipse.jetty.util.component;

import java.io.IOException;

public interface Dumpable {
	String dump();// 直接DUMP成字符串
	void dump(Appendable out, String indent) throws IOException;// 将内容DUMP到第一个参数中
}
