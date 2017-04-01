package org.eclipse.jetty.embedded;
//
// import org.eclipse.jetty.server.Server;
// import org.eclipse.jetty.servlet.ServletContextHandler;
// import org.eclipse.jetty.servlet.ServletHolder;
// import org.eclipse.jetty.websocket.api.Session;
// import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
// import org.eclipse.jetty.websocket.api.annotations.WebSocket;
// import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
// import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
//
/// **
// * Example of setting up a Jetty WebSocket server
// * Note: this uses the Jetty WebSocket API, not the javax.websocket API.
// */
// public class WebSocketServer {
// /**
// * Example of a Jetty API WebSocket Echo Socket
// */
// @WebSocket
// public static class EchoSocket {
// @OnWebSocketMessage
// public void onMessage(Session session, String message) {
// session.getRemote().sendStringByFuture(message);
// }
// }
//
// /**
// * Servlet layer
// */
// @SuppressWarnings("serial")
// public static class EchoServlet extends WebSocketServlet {
// @Override
// public void configure(WebSocketServletFactory factory) {
// // Register the echo websocket with the basic WebSocketCreator
// factory.register(EchoSocket.class);
// }
// }
//
// public static void main(String[] args) throws Exception {
//
// Server server = new Server(8080);
//
// ServletContextHandler context = new
// ServletContextHandler(ServletContextHandler.SESSIONS);
// context.setContextPath("/");
// server.setHandler(context);
//
// //add the echo socket servlet to the /echo path map
// context.addServlet(new ServletHolder(EchoServlet.class), "/echo");
//
// server.start();
// context.dumpStdErr();
// server.join();
// }
// }
