package org.eclipse.jetty.runner;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ConnectorStatistics;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.ShutdownMonitor;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.StatisticsServlet;
import org.eclipse.jetty.util.RolloverFileOutputStream;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;

import extral.org.eclipse.jetty.webapp.WebAppContext;
import extral.org.eclipse.jetty.xml.XmlConfiguration;

public class Runner {
	
    private static final Logger LOG = Log.getLogger(Runner.class);

    public static final String[] __plusConfigurationClasses = new String[] {
			extral.org.eclipse.jetty.webapp.WebInfConfiguration.class.getCanonicalName(),
			extral.org.eclipse.jetty.webapp.WebXmlConfiguration.class.getCanonicalName(),
			extral.org.eclipse.jetty.webapp.MetaInfConfiguration.class.getCanonicalName(),
			extral.org.eclipse.jetty.webapp.FragmentConfiguration.class.getCanonicalName(),
			org.eclipse.jetty.plus.webapp.EnvConfiguration.class.getCanonicalName(),
			org.eclipse.jetty.plus.webapp.PlusConfiguration.class.getCanonicalName(),
			org.eclipse.jetty.annotations.AnnotationConfiguration.class.getCanonicalName(),
			extral.org.eclipse.jetty.webapp.JettyWebXmlConfiguration.class.getCanonicalName()
            };

    public static final String __containerIncludeJarPattern =  ".*/jetty-runner-[^/]*\\.jar$";
    public static final String __defaultContextPath = "/";

    public static final int __defaultPort = 8080;

    protected Server _server;
	protected URLClassLoader _classLoader;// 类加载器(用于加载下面参数指定的类)
	protected Classpath _classpath = new Classpath();// 表示解析到的参数类路径
    protected ContextHandlerCollection _contexts;
    protected RequestLogHandler _logHandler;
	protected String _logFile;// ????
    protected ArrayList<String> _configFiles;
	protected boolean _enableStats = false;// 统计相关
    protected String _statsPropFile;

	public class Classpath {// 用于封装一个参数类路径

        private  List<URL> _classpath = new ArrayList<>();

		@SuppressWarnings("deprecation")
		public void addJars(Resource lib) throws IOException {// 表示添加的是目录

			if (lib == null || !lib.exists()) {
				throw new IllegalStateException("no such lib: " + lib);
			}

			String[] list = lib.list();// 获取目录中的所有文件
			if (list == null) {
				return;
			}

			for (String path : list) {
				if (".".equals(path) || "..".equals(path)) {// 这两个目录先不管
					continue;
				}
				try (Resource item = lib.addPath(path)) {
					if (item.isDirectory()) {
						addJars(item);// 循环添加
					} else {
                        String lowerCasePath = path.toLowerCase(Locale.ENGLISH);
						if (lowerCasePath.endsWith(".jar") || lowerCasePath.endsWith(".zip")) {
                            URL url = item.getURL();
                            _classpath.add(url);
                        }
                    }
                }
            }
        }
		@SuppressWarnings("deprecation")
		public void addPath(Resource path) {// 表示添加的是路径
			if (path == null || !path.exists()) {
				throw new IllegalStateException("no such path: " + path);
			}
            _classpath.add(path.getURL());
        }
		public URL[] asArray() {
            return _classpath.toArray(new URL[_classpath.size()]);
        }
    }

	public Runner() {
    }

	public void usage(String error) {// 参数表示错误的输入

		if (error != null) {
			System.err.println("ERROR: " + error);
		}

        System.err.println("Usage: java [-Djetty.home=dir] -jar jetty-runner.jar [--help|--version] [ server opts] [[ context opts] context ...] ");
        System.err.println("Server opts:");
        System.err.println(" --version                           - display version and exit");
        System.err.println(" --log file                          - request log filename (with optional 'yyyy_mm_dd' wildcard");
        System.err.println(" --out file                          - info/warn/debug log filename (with optional 'yyyy_mm_dd' wildcard");
        System.err.println(" --host name|ip                      - interface to listen on (default is all interfaces)");
        System.err.println(" --port n                            - port to listen on (default 8080)");
        System.err.println(" --stop-port n                       - port to listen for stop command");
        System.err.println(" --stop-key n                        - security string for stop command (required if --stop-port is present)");
        System.err.println(" [--jar file]*n                      - each tuple specifies an extra jar to be added to the classloader");
        System.err.println(" [--lib dir]*n                       - each tuple specifies an extra directory of jars to be added to the classloader");
        System.err.println(" [--classes dir]*n                   - each tuple specifies an extra directory of classes to be added to the classloader");
        System.err.println(" --stats [unsecure|realm.properties] - enable stats gathering servlet context");
        System.err.println(" [--config file]*n                   - each tuple specifies the name of a jetty xml config file to apply (in the order defined)");
        System.err.println("Context opts:");
        System.err.println(" [[--path /path] context]*n          - WAR file, web app dir or context xml file, optionally with a context path");
        System.exit(1);
    }
	public void version() {
		System.err.println("org.eclipse.jetty.runner.Runner: " + Server.getVersion());
        System.exit(1);
    }

	@SuppressWarnings("deprecation")
	public void configure(String[] args) throws Exception {// 参数为传递给RUNNER的参数

		for (int i = 0; i < args.length; i++) {// 首先遍历一遍
			if ("--lib".equals(args[i])) {// 库文件所在位置(必须是个目录)
				try (Resource lib = Resource.newResource(args[++i])) {
					if (!lib.exists() || !lib.isDirectory()) {
						usage("no such lib directory " + lib);
					}
					_classpath.addJars(lib);// 这里是JAR包(是文件而不是路径)
                }
			} else if ("--jar".equals(args[i])) {// JAR文件
				try (Resource jar = Resource.newResource(args[++i])) {
					if (!jar.exists() || jar.isDirectory()) {
						usage("no such jar " + jar);
					}
                    _classpath.addPath(jar);
                }
			} else if ("--classes".equals(args[i])) {// 类路径
				try (Resource classes = Resource.newResource(args[++i])) {
					if (!classes.exists() || !classes.isDirectory()) {
						usage("no such classes directory " + classes);
					}
                    _classpath.addPath(classes);
                }
			} else if (args[i].startsWith("--")) {
				i++;
            }
        }

		initClassLoader();// 初始化类加载器

		LOG.info("runner");

		LOG.debug("runner classpath {}", _classpath);

		String contextPath = __defaultContextPath;// 上下文路径(提供默认值)

		boolean contextPathSet = false;// 标志是否设置过

		int port = __defaultPort;//

        String host = null;

		int stopPort = 0;// ????

        String stopKey = null;

		boolean runnerServerInitialized = false;// 对应的服务器是否初始化过

		for (int i = 0; i < args.length; i++) {// 获取运行参数(再次遍历一遍)
			switch (args[i]) {
                case "--port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "--host":
                    host = args[++i];
                    break;
                case "--stop-port":
                    stopPort = Integer.parseInt(args[++i]);
                    break;
                case "--stop-key":
                    stopKey = args[++i];
                    break;
                case "--log":
                    _logFile = args[++i];
                    break;
                case "--out":
                    String outFile = args[++i];
                    PrintStream out = new PrintStream(new RolloverFileOutputStream(outFile, true, -1));// 日志设置
                    LOG.info("redirecting stderr/stdout to " + outFile);
                    System.setErr(out);
                    System.setOut(out);
                    break;
                case "--path":
                    contextPath = args[++i];// HTTP路径设置
                    contextPathSet = true;
                    break;
                case "--config":
					if (_configFiles == null) {
						_configFiles = new ArrayList<>();
					}
					_configFiles.add(args[++i]);// 配置文件
                    break;
                case "--lib":
                    ++i;
                    break;
                case "--jar":
                    ++i;
                    break;
                case "--classes":
                    ++i;
                    break;
                case "--stats":
                    _enableStats = true;
                    _statsPropFile = args[++i];
                    _statsPropFile = ("unsecure".equalsIgnoreCase(_statsPropFile) ? null : _statsPropFile);
                    break;
                default:
                	if (!runnerServerInitialized) {// 表示服务器还未初始化
                		if (_server == null) {
                            _server = new Server();
                        }
                		if (_configFiles != null) {// 配置文件--表示通过配置文件进行启动
                			for (String cfg : _configFiles) {
                                try (Resource resource = Resource.newResource(cfg)) {
                                	XmlConfiguration xmlConfiguration = new XmlConfiguration(resource.getURL());
                                	xmlConfiguration.configure(_server);// ????
                                }
                            }
                        }
                        HandlerCollection handlers = (HandlerCollection) _server.getChildHandlerByClass(HandlerCollection.class);
                        if (handlers == null) {
                            handlers = new HandlerCollection();
                            _server.setHandler(handlers);
                        }
                        _contexts = (ContextHandlerCollection) handlers.getChildHandlerByClass(ContextHandlerCollection.class);
                        if (_contexts == null) {
                            _contexts = new ContextHandlerCollection();
                            prependHandler(_contexts, handlers);
                        }
                        if (_enableStats) {// 统计功能相关
                            if (handlers.getChildHandlerByClass(StatisticsHandler.class) == null) {
                                
                            	StatisticsHandler statsHandler = new StatisticsHandler();
                                Handler oldHandler = _server.getHandler();// 表示用统计HANDLER包装其他全部的处理器
                                statsHandler.setHandler(oldHandler);
                                _server.setHandler(statsHandler);
                                
                                ServletContextHandler statsContext = new ServletContextHandler(_contexts, "/stats");
                                statsContext.addServlet(new ServletHolder(new StatisticsServlet()), "/");
                                statsContext.setSessionHandler(new SessionHandler());

                                if (_statsPropFile != null) {

                                    HashLoginService loginService = new HashLoginService("StatsRealm", _statsPropFile);
                                    Constraint constraint = new Constraint();
                                    constraint.setName("Admin Only");
                                    constraint.setRoles(new String[]{"admin"});
                                    constraint.setAuthenticate(true);

                                    ConstraintMapping cm = new ConstraintMapping();
                                    cm.setConstraint(constraint);
                                    cm.setPathSpec("/*");

                                    ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
                                    securityHandler.setLoginService(loginService);
                                    securityHandler.setConstraintMappings(Collections.singletonList(cm));
                                    securityHandler.setAuthenticator(new BasicAuthenticator());
                                    statsContext.setSecurityHandler(securityHandler);
                                }
                            }
                        }

						if (handlers.getChildHandlerByClass(DefaultHandler.class) == null) {
                            handlers.addHandler(new DefaultHandler());
                        }

                        _logHandler = (RequestLogHandler) handlers.getChildHandlerByClass(RequestLogHandler.class);
                        if (_logHandler == null) {
                            _logHandler = new RequestLogHandler();
                            handlers.addHandler(_logHandler);
                        }

                        Connector[] connectors = _server.getConnectors();
                        if (connectors == null || connectors.length == 0) {
                            ServerConnector connector = new ServerConnector(_server);
                            connector.setPort(port);
							if (host != null) {
								connector.setHost(host);
							}
							_server.addConnector(connector);
							if (_enableStats) {
								connector.addBean(new ConnectorStatistics());
							}
						} else {
							if (_enableStats) {
								for (Connector connector : connectors) {
                                    ((AbstractConnector) connector).addBean(new ConnectorStatistics());
                                }
                            }
                        }
                        runnerServerInitialized = true;
                    }
					try (Resource ctx = Resource.newResource(args[i])) {
						if (!ctx.exists()) {
							usage("Context '" + ctx + "' does not exist");
						}
	
						if (contextPathSet && !(contextPath.startsWith("/"))) {
							contextPath = "/" + contextPath;
							}
	
						if (!ctx.isDirectory() && ctx.toString().toLowerCase(Locale.ENGLISH).endsWith(".xml")) {
							XmlConfiguration xmlConfiguration = new XmlConfiguration(ctx.getURL());
							xmlConfiguration.getIdMap().put("Server", _server);
							ContextHandler handler = (ContextHandler) xmlConfiguration.configure();
							if (contextPathSet) {
								handler.setContextPath(contextPath);
							}
							_contexts.addHandler(handler);
							handler.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
									__containerIncludeJarPattern);
						} else {
							WebAppContext webapp = new WebAppContext(_contexts, ctx.toString(), contextPath);
							webapp.setConfigurationClasses(__plusConfigurationClasses);
							webapp.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", __containerIncludeJarPattern);
                        }
                    }
                    contextPathSet = false;
                    contextPath = __defaultContextPath;
                    break;
            }
        }

		if (_server == null) {
			usage("no contexts defined");
		}
        _server.setStopAtShutdown(true);

		switch ((stopPort > 0 ? 1 : 0) + (stopKey != null ? 2 : 0)) {
            case 1:
                usage("Must specify --stop-key when --stop-port is specified");
                break;
            case 2:
                usage("Must specify --stop-port when --stop-key is specified");
                break;
            case 3:
                ShutdownMonitor monitor = ShutdownMonitor.getInstance();
                monitor.setPort(stopPort);
                monitor.setKey(stopKey);
                monitor.setExitVm(true);
                break;
        }

		if (_logFile != null) {
            NCSARequestLog requestLog = new NCSARequestLog(_logFile);
            requestLog.setExtended(false);
            _logHandler.setRequestLog(requestLog);
        }
    }

	protected void prependHandler(Handler handler, HandlerCollection handlers) {// ????
		if (handler == null || handlers == null) {
			return;
		}
		Handler[] existing = handlers.getChildHandlers();
		Handler[] children = new Handler[existing.length + 1];
		children[0] = handler;
		System.arraycopy(existing, 0, children, 1, existing.length);// 表示将第一个参数从第二个参数指定的位置开始的元素存放到后面位置处
		handlers.setHandlers(children);
	}

	public void run() throws Exception {// 表示启动服务
        _server.start();
        _server.join();
    }

	protected void initClassLoader() {

		URL[] paths = _classpath.asArray();// 表示所有的可运行文件

		if (_classLoader == null && paths != null && paths.length > 0) {

			ClassLoader context = Thread.currentThread().getContextClassLoader();// 当前上下文环境类加载器

			if (context == null) {
				_classLoader = new URLClassLoader(paths);// 采用默认
			} else {
				_classLoader = new URLClassLoader(paths, context);// 采用指定上下文环境类加载器
			}

			Thread.currentThread().setContextClassLoader(_classLoader);// 给当前线程设置类加载器
        }
    }

	public static void main(String[] args) {

        Runner runner = new Runner();

		try {
			if (args.length > 0 && args[0].equalsIgnoreCase("--help")) {// 必须是首个参数
                runner.usage(null);
			} else if (args.length > 0 && args[0].equalsIgnoreCase("--version")) {// 必须是首个参数
                runner.version();
			} else {
				runner.configure(args);// 配置服务器
				runner.run();// 直接运行服务
            }
		} catch (Exception e) {
            e.printStackTrace();
            runner.usage(null);
        }
    }
}
