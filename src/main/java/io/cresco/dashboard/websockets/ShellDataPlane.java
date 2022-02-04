package io.cresco.dashboard.websockets;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.cresco.dashboard.Plugin;
import io.cresco.library.data.TopicType;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.jms.Message;
import javax.jms.TextMessage;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ClientEndpoint
@ServerEndpoint(value="/dashboard/shellstream")
public class ShellDataPlane
{
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String,ShellInfo> sessionMap = Collections.synchronizedMap(new HashMap<>());
    private static final Type hashtype = new TypeToken<Map<String, String>>(){}.getType();
    private static final Gson gson = new Gson();
    private AtomicBoolean lockSessions = new AtomicBoolean();
    private AtomicBoolean lockSessionMap = new AtomicBoolean();
    //synchronized (lockConfig) {

    private PluginBuilder plugin;
    private CLogger logger;

    public ShellDataPlane() {

        if(plugin == null) {
            if(Plugin.pluginBuilder != null) {
                plugin = Plugin.pluginBuilder;
                logger = plugin.getLogger(ShellDataPlane.class.getName(), CLogger.Level.Info);
            }
        }

    }

    @OnOpen
    public void onWebSocketConnect(Session sess)
    {
        sess.setMaxBinaryMessageBufferSize(50000000);
        sess.setMaxTextMessageBufferSize(50000000);
        synchronized (lockSessions) {
            sessions.add(sess);
        }

    }

    private boolean isActive(Session sess) {
        boolean isActive = false;
        try {

            synchronized (lockSessionMap) {
                if(sessionMap.containsKey(sess.getId())) {
                    isActive = true;
                }
            }

        } catch (Exception ex) {
            logger.error("isActive() " + ex.getMessage());
        }

        return isActive;
    }

    @OnMessage
    public void onWebSocketText(Session sess, String message)
    {

        if(isActive(sess)) {

            try {

                String identKey;
                String identId;
                String ioTypeKey;
                String inputId;

                synchronized (lockSessionMap) {
                    identKey = sessionMap.get(sess.getId()).getIdentKey();
                    identId = sessionMap.get(sess.getId()).getIdentId();
                    ioTypeKey = sessionMap.get(sess.getId()).getIoTypeKey();
                    inputId = sessionMap.get(sess.getId()).getInputId();
                }

                if((identKey != null) && (identId != null)) {
                    TextMessage updateMessage = plugin.getAgentService().getDataPlaneService().createTextMessage();
                    updateMessage.setText(message);
                    updateMessage.setStringProperty(identKey, identId);
                    updateMessage.setStringProperty(ioTypeKey, inputId);

                    plugin.getAgentService().getDataPlaneService().sendMessage(TopicType.AGENT, updateMessage);
                } else {
                    logger.error("Active onWebSocketText(): must provide identKey and identID");
                }
            } catch (Exception ex) {
                logger.error("onWebSocketText: " + ex.getMessage());
            }

        } else {


            Map<String, String> responce = new HashMap<>();
            //responce.put("stream_query", message);
            try {
                
                Map<String,String> mapMessage = gson.fromJson(message,hashtype);
                ShellInfo streamInfo = new ShellInfo(sess.getId(),mapMessage.get("ident_key"), mapMessage.get("ident_id"));
                streamInfo.setIoTypeKey(mapMessage.get("io_type_key"));
                streamInfo.setOutputId(mapMessage.get("output_id"));
                streamInfo.setInputId(mapMessage.get("input_id"));

                if (createListener(sess, streamInfo)) {
                    responce.put("status_code", "10");
                    responce.put("status_desc", "Listener Active");

                } else {
                    responce.put("status_code", "9");
                    responce.put("status_desc", "Could not activate listener");

                }

            } catch (Exception ex) {
                ex.printStackTrace();
                responce.put("status_code", "90");
                responce.put("status_desc", ex.getMessage());
                ex.printStackTrace();
            }
            
            sess.getAsyncRemote().sendObject(gson.toJson(responce));

        }

    }

    private boolean createListener(Session sess, ShellInfo streamInfo) {
        boolean isCreated = false;
        try{

            javax.jms.MessageListener ml = new javax.jms.MessageListener() {
                public void onMessage(Message msg) {
                    try {


                        if (msg instanceof TextMessage) {

                            sess.getAsyncRemote().sendObject(((TextMessage) msg).getText());

                        }
                    } catch(Exception ex) {

                        ex.printStackTrace();
                    }
                }
            };

            String stream_query = streamInfo.getIdentKey() + "='" + streamInfo.getIdentId() + "' and " + streamInfo.getIoTypeKey() + "='" + streamInfo.getOutputId() + "'";

            String listenerid = plugin.getAgentService().getDataPlaneService().addMessageListener(TopicType.AGENT,ml,stream_query);

            streamInfo.setListenerId(listenerid);

            synchronized (lockSessionMap) {
                sessionMap.put(sess.getId(), streamInfo);
            }

            //sess.getAsyncRemote().sendObject("APIDataPlane Connected Session: " + sess.getId());

            isCreated = true;

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return isCreated;
    }

    @OnClose
    public void onWebSocketClose(Session sess, CloseReason reason)
    {
        //logger.info("Socket Closed: " + reason);
        //System.out.println("Socket Closed: " + reason);
        String listenerid = null;
        synchronized (lockSessionMap) {
            listenerid = sessionMap.get(sess.getId()).getSessionId();
        }
        //so we don't get messages about disabling logger
        if (listenerid != null) {
            Plugin.pluginBuilder.getAgentService().getDataPlaneService().removeMessageListener(listenerid);
        } else {
            logger.error("onWebSocketClose(): sessionMap = null : closed: " + reason.getReasonPhrase());
        }

        /*
        if(activeHost.containsKey(sess.getId())) {
            SessionInfo sessionInfo = activeHost.get(sess.getId());
            logger.error("removing sessionId: " + sessionInfo.logSessionId + " from regionId: " + sessionInfo.regionId + " agentId: " + sessionInfo.agentId);
        }

         */
        synchronized (lockSessions) {
            sessions.remove(sess);
        }
    }

    @OnError
    public void onWebSocketError(Throwable cause)
    {
        cause.printStackTrace(System.err);
    }


    public void broadcast(String message) {

        synchronized (sessions) {
            synchronized (lockSessions) {
                sessions.forEach(session -> {
                    if (session.isOpen()) {
                        session.getAsyncRemote().sendObject(message);
                    }
                });
            }
        }
    }
}