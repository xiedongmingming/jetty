package org.eclipse.jetty.server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jetty.util.component.Destroyable;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.ShutdownThread;



public class ShutdownMonitor {//shutdown/stop monitor thread.
    //**************************************************************
	private static class Holder {//implementation of safe lazy init, using initialization on demand holder technique.
        static ShutdownMonitor instance = new ShutdownMonitor();
    }
    //******************************************************************
	public static ShutdownMonitor getInstance() {
		return Holder.instance;// 鍙湁鍦ㄦ鏃舵墠鍘诲垵濮嬪寲
    }
	//******************************************************************
    protected static void reset() {
        Holder.instance = new ShutdownMonitor();
    }
    public static void register(LifeCycle... lifeCycles) {
        getInstance().addLifeCycles(lifeCycles);
    }
    public static void deregister(LifeCycle lifeCycle) {
        getInstance().removeLifeCycle(lifeCycle);
    }
    public static boolean isRegistered(LifeCycle lifeCycle) {
        return getInstance().containsLifeCycle(lifeCycle);
    }
	//**************************************************************
	private final Set<LifeCycle> _lifeCycles = new LinkedHashSet<>();// 娉ㄥ唽鐨勬墍鏈夌敓鍛藉懆鏈�
	
	//**************************************************************
	// 涓嬮潰杩欎簺鍙傛暟鍦ㄦ瀯閫犲嚱鏁颁腑鍒濆鍖� -- 浣跨敤绯荤粺灞炴��
    private boolean debug;//
    private final String host;//
	private int port;// 绔彛
	private String key;// 绉橀挜
    private boolean exitVm;//
	
	private boolean alive;// 璇ョ嚎绋嬫槸鍚﹀凡缁忓紑濮�

    private ShutdownMonitor() {
        this.debug = System.getProperty("DEBUG") != null;
        this.host = System.getProperty("STOP.HOST", "127.0.0.1");
        this.port = Integer.parseInt(System.getProperty("STOP.PORT", "-1"));
        this.key = System.getProperty("STOP.KEY", null);
        this.exitVm = true;
    }
    private void addLifeCycles(LifeCycle... lifeCycles) {
        synchronized (this) {
            _lifeCycles.addAll(Arrays.asList(lifeCycles));
        }
    }
    private void removeLifeCycle(LifeCycle lifeCycle) {
        synchronized (this) {
            _lifeCycles.remove(lifeCycle);
        }
    }
    private boolean containsLifeCycle(LifeCycle lifeCycle) {
        synchronized (this) {
            return _lifeCycles.contains(lifeCycle);
        }
    }
    private void debug(String format, Object... args) {
        if (debug) {
            System.err.printf("[ShutdownMonitor] " + format + "%n", args);
		}
    }
    private void debug(Throwable t) {
        if (debug) {
            t.printStackTrace(System.err);
		}
    }
    public String getKey() {
        synchronized (this) {
            return key;
        }
    }
    public int getPort() {
        synchronized (this) {
            return port;
        }
    }
    public boolean isExitVm() {
        synchronized (this) {
            return exitVm;
        }
    }
    public void setDebug(boolean flag) {
        this.debug = flag;
    }
	//**************************************************************
	// 寮�濮嬪悗涓嶈兘鍙樺寲
    public void setExitVm(boolean exitVm) {
        synchronized (this) {
            if (alive) {
                throw new IllegalStateException("ShutdownMonitor already started");
			}
            this.exitVm = exitVm;
        }
    }
    public void setKey(String key) {
        synchronized (this) {
            if (alive) {
                throw new IllegalStateException("ShutdownMonitor already started");
			}
            this.key = key;
        }
    }
    public void setPort(int port) {
        synchronized (this) {
            if (alive) {
                throw new IllegalStateException("ShutdownMonitor already started");
			}
            this.port = port;
        }
    }
	//**************************************************************
	protected void start() throws Exception {// 寮�濮嬫绾跨▼ -- 浠ュ悗鍙板舰寮忔墽琛�
        synchronized (this) {
            if (alive) {
                debug("already started");
                return;//cannot start it again
            }
            ServerSocket serverSocket = listen();
            if (serverSocket != null) {
				alive = true;// 琛ㄧず璇ョ洃瑙嗗櫒宸茬粡鍚姩
                Thread thread = new Thread(new ShutdownMonitorRunnable(serverSocket));
                thread.setDaemon(true);
                thread.setName("ShutdownMonitor");
                thread.start();
            }
        }
    }
    private void stop() {
        synchronized (this) {
            alive = false;
			notifyAll();// OBJECT瀵硅薄涓殑鐢ㄤ簬閫氱煡鎵�鏈夌瓑寰�
        }
    }
    void await() throws InterruptedException {//for test purposes only.
        synchronized (this) {
            while (alive) {
                wait();//???
            }
        }
    }
    protected boolean isAlive() {
        synchronized (this) {
            return alive;
        }
    }

	private ServerSocket listen() {// 鐩戝惉
        int port = getPort();
        if (port < 0)  {
            debug("not enabled (port < 0): %d", port);
            return null;
        }
        String key = getKey();
        try {
            ServerSocket serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);// 鏂逛究閲嶇敤SOCKET
			serverSocket.bind(new InetSocketAddress(InetAddress.getByName(host), port));// 缁戝畾
			if (port == 0) {// 鎵嶇敤鑷姩鍒嗛厤鐨勭鍙�
                port = serverSocket.getLocalPort();
                System.out.printf("STOP.PORT=%d%n", port);
                setPort(port);
            }
			if (key == null) {// 鑷姩鐢熸垚涓�涓狵EY
                key = Long.toString((long)(Long.MAX_VALUE * Math.random() + this.hashCode() + System.currentTimeMillis()), 36);
                System.out.printf("STOP.KEY=%s%n", key);
                setKey(key);
            }
            return serverSocket;
        } catch (Throwable x) {
            debug(x);
            System.err.println("Error binding ShutdownMonitor to port " + port + ": " + x.toString());
            return null;
        } finally {//establish the port and key that are in use
            debug("STOP.PORT=%d", port);
            debug("STOP.KEY=%s", key);
        }
    }

	private class ShutdownMonitorRunnable implements Runnable {// 璇ョ嚎绋嬭繍琛岀殑浠ｇ爜鍧�
		private final ServerSocket serverSocket;
		private ShutdownMonitorRunnable(ServerSocket serverSocket) {
			this.serverSocket = serverSocket;// 宸茬粡鍒濆鍖栬繃浜�
        }
        @Override
        public void run() {
            debug("Started");
            try {
                String key = getKey();
				while (true) {// 鐩戝惉
                    try (Socket socket = serverSocket.accept()) {
						LineNumberReader reader = new LineNumberReader(new InputStreamReader(socket.getInputStream()));// 鎸夎璇诲彇鍚屾椂鍙互鑾峰彇琛屽彿
                        //**********************************************************
						// 1銆佸厛楠岃瘉KEY
						String receivedKey = reader.readLine();
                        if (!key.equals(receivedKey)) {
                            debug("ignoring command with incorrect key: %s", receivedKey);
                            continue;
                        }
						//**********************************************************
						// 2銆佸湪鑾峰彇鍛戒护
                        String cmd = reader.readLine();
                        debug("command=%s", cmd);
                        OutputStream out = socket.getOutputStream();
                        boolean exitVm = isExitVm();
                        if ("stop".equalsIgnoreCase(cmd)) {//stop
                            debug("performing stop command");
                            stopLifeCycles(ShutdownThread::isRegistered, exitVm);
                            debug("informing client that we are stopped");
                            informClient(out, "stopped\r\n");
							if (!exitVm) {// 琛ㄧず涓嶉��鍑篔VM
								break;
							}
                            debug("killing jvm");
                            System.exit(0);
                        } else if ("forcestop".equalsIgnoreCase(cmd)) {//forcestop
                            debug("performing forced stop command");
							stopLifeCycles(l -> true, exitVm);// 绗竴涓�艰〃绀轰紶鍏ョ殑鍙傛暟(鍚庨潰鐨勪负杩斿洖鐨勫��)
                            debug("informing client that we are stopped");
                            informClient(out, "stopped\r\n");
                            if (!exitVm) {
                                break;
							}
                            debug("killing jvm");
                            System.exit(0);
                        } else if ("stopexit".equalsIgnoreCase(cmd)) {//stopexit
                            debug("performing stop and exit commands");
                            stopLifeCycles(ShutdownThread::isRegistered, true);
                            debug("informing client that we are stopped");
                            informClient(out, "stopped\r\n");
                            debug("killing jvm");
                            System.exit(0);
                        } else if ("exit".equalsIgnoreCase(cmd)) {//eixt
                            debug("killing jvm");
                            System.exit(0);
                        } else if ("status".equalsIgnoreCase(cmd)) {//status
                            informClient(out, "ok\r\n");
                        }
                    } catch (Throwable x) {
                        debug(x);
                    }
                }
            } catch (Throwable x) {
                debug(x);
            } finally {
                stop();
                debug("stopped");
            }
        }

		private void informClient(OutputStream out, String message) throws IOException {// 鐢ㄤ簬缁欏鎴风鍙戦�佷俊鎭�
            out.write(message.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

		private void stopLifeCycles(Predicate<LifeCycle> predicate, boolean destroy) {// 绗竴涓弬鏁拌〃绀轰竴涓祴璇曟帴鍙�(LAMDBA琛ㄨ揪寮�)
            List<LifeCycle> lifeCycles = new ArrayList<>();
            synchronized (this) {
                lifeCycles.addAll(_lifeCycles);
            }
            for (LifeCycle l : lifeCycles) {
                try {
                    if (l.isStarted() && predicate.test(l)) {//
                        l.stop();
					}
                    if ((l instanceof Destroyable) && destroy) {
                        ((Destroyable)l).destroy();
					}
                } catch (Throwable x) {
                    debug(x);
                }
            }
        }
    }
    @Override
    public String toString() {
        return String.format("%s[port=%d,alive=%b]", this.getClass().getName(), getPort(), isAlive());
    }
}
