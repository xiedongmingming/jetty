package org.eclipse.jetty.util;

public class Jetty {// 版本相关信息

    public static final String VERSION;
    public static final String POWERED_BY;
    public static final boolean STABLE;

	static {

		Package pkg = Jetty.class.getPackage();// 获取类所在位置的包文件信息

		if (pkg != null && "eclipse.org - jetty".equals(pkg.getImplementationVendor()) && pkg.getImplementationVersion() != null) {
			VERSION = pkg.getImplementationVersion();
		} else {
			VERSION = System.getProperty("jetty.version", "9.3.z-SNAPSHOT");
		}
		POWERED_BY = "<a href=\"http://eclipse.org/jetty\">Powered by Jetty:// " + VERSION + "</a>";
        STABLE = !VERSION.matches("^.*\\.(RC|M)[0-9]+$");
    }

	private Jetty() {
    }
}
