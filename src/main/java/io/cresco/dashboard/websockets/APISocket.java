package io.cresco.dashboard.websockets;

import io.cresco.dashboard.Plugin;
import io.cresco.dashboard.controllers.AgentsController;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.util.*;

@ClientEndpoint
@ServerEndpoint(value="/dashboard/apisocket")
public class APISocket
{
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String,SessionInfo> activeHost = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String,String> sessionMap = Collections.synchronizedMap(new HashMap<>());


    private PluginBuilder plugin;
    private CLogger logger;

    public APISocket() {

        if(plugin == null) {
            if(Plugin.pluginBuilder != null) {
                plugin = Plugin.pluginBuilder;
                logger = plugin.getLogger(AgentsController.class.getName(), CLogger.Level.Info);
            }
        }

    }

    @OnOpen
    public void onWebSocketConnect(Session sess)
    {
        sessions.add(sess);
        String logSessionId = UUID.randomUUID().toString();
        sessionMap.put(sess.getId(),logSessionId);
        //System.out.println("Socket Connected: " + sess);
        logger.info("Socket Connected: " + sess.getId());

    }

    @OnMessage
    public void onWebSocketText(Session sess, String message)
    {
        logger.info("Received TEXT message: " + message);

        String respMessage = "THIS WORKED";
        sess.getAsyncRemote().sendObject(respMessage);

    }

    @OnClose
    public void onWebSocketClose(Session sess, CloseReason reason)
    {
        logger.info("Socket Closed: " + reason);
        //System.out.println("Socket Closed: " + reason);

        if(activeHost.containsKey(sess.getId())) {
            SessionInfo sessionInfo = activeHost.get(sess.getId());
            logger.error("removing sessionId: " + sessionInfo.logSessionId + " from regionId: " + sessionInfo.regionId + " agentId: " + sessionInfo.agentId);
        }

        sessions.remove(sess);
    }

    @OnError
    public void onWebSocketError(Throwable cause)
    {
        cause.printStackTrace(System.err);
    }


    public void broadcast(String message) {

        synchronized (sessions) {
            sessions.forEach(session -> {
                if (session.isOpen()) {
                    session.getAsyncRemote().sendObject(message);
                }
            });
        }
    }
}