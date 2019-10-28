package io.cresco.dashboard.websockets;

import javax.servlet.annotation.WebServlet;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

@SuppressWarnings("serial")
@WebServlet(name = "MyEcho WebSocket Servlet", urlPatterns = { "/echo" })
public class MyEchoServlet extends WebSocketServlet
{
    @Override
    public void configure(WebSocketServletFactory factory)
    {
        // set a 10 second timeout
        factory.getPolicy().setIdleTimeout(10000);

        // register MyEchoSocket as the WebSocket to create on Upgrade
        factory.register(MyEchoSocket.class);
    }
}