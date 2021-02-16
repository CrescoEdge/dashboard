package io.cresco.dashboard.websockets;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.cresco.dashboard.Plugin;
import io.cresco.dashboard.controllers.AgentsController;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.lang.reflect.Type;
import java.util.*;

@ClientEndpoint
@ServerEndpoint(value="/dashboard/apisocket")
public class APISocket
{
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String,SessionInfo> activeHost = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String,String> sessionMap = Collections.synchronizedMap(new HashMap<>());
    private static final Type type = new TypeToken<Map<String, Map<String, String>>>(){}.getType();
    private static final Gson gson = new Gson();


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
        String r = "{\"error\":\"unknown error\"}";

        logger.info("Received TEXT message: " + message);


        Map<String, Map<String, String>> incoming_message = gson.fromJson(message, type);

        MsgEvent request = GetResponceMsgEvent(incoming_message);

        MsgEvent response = plugin.sendRPC(request);



        if (response == null)
            r = "{\"error\":\"Cresco rpc response was null\"}";
        else {
            r = gson.toJson(response.getParams());
        }

        sess.getAsyncRemote().sendObject(r);

    /*
    global_plugin_msgevent
    public MsgEvent getGlobalPluginMsgEvent(MsgEvent.Type type, String dstRegion, String dstAgent, String dstPlugin) {
    public MsgEvent getKPIMsgEvent() {
    public MsgEvent getRegionalControllerMsgEvent(MsgEvent.Type type) {
    public MsgEvent getRegionalAgentMsgEvent(MsgEvent.Type type, String dstAgent) {
    public MsgEvent getRegionalPluginMsgEvent(MsgEvent.Type type, String dstAgent, String dstPlugin) {
    public MsgEvent getAgentMsgEvent(MsgEvent.Type type) {
    public MsgEvent getPluginMsgEvent(MsgEvent.Type type, String dstPlugin) {
    */


    }

    private MsgEvent GetResponceMsgEvent(Map<String, Map<String, String>> incoming_message) {
        MsgEvent request = null;
        try {

            Map<String,String> messageInfo = incoming_message.get("message_info");
            Map<String,String> messagePayload = incoming_message.get("message_payload");

            String messageType = messageInfo.get("message_type");

            switch (messageType) {

                case "global_controller_msgevent":
                    request = GlobalControllerMsgEvent(messageInfo);
                    break;
                case "global_agent_msgevent":
                    request = GlobalAgentMsgEvent(messageInfo);
                    break;
                case "global_plugin_msgevent":
                    request = GlobalPluginMsgEvent(messageInfo);
                    break;
                case "plugin_msgevent":
                    request = PluginMsgEvent(messageInfo);
                    break;



                default:
                    logger.error("Unknown message type");

            }

            for (Map.Entry<String, String> entry : messagePayload.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                request.setParam(key, value);
            }


        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }

    private MsgEvent GlobalControllerMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

            MsgEvent.Type messageEventType = MsgEvent.Type.valueOf(messageInfo.get("message_event_type"));
            request = plugin.getGlobalControllerMsgEvent(messageEventType);

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }

    private MsgEvent GlobalAgentMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

            MsgEvent.Type messageEventType = MsgEvent.Type.valueOf(messageInfo.get("message_event_type"));
            String dstRegion = messageInfo.get("dst_region");
            String dstAgent = messageInfo.get("dst_agent");
            request = plugin.getGlobalAgentMsgEvent(messageEventType, dstRegion, dstAgent);

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }

    private MsgEvent GlobalPluginMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

            MsgEvent.Type messageEventType = MsgEvent.Type.valueOf(messageInfo.get("message_event_type"));
            String dstRegion = messageInfo.get("dst_region");
            String dstAgent = messageInfo.get("dst_agent");
            String dstPlugin = messageInfo.get("dst_plugin");
            request = plugin.getGlobalPluginMsgEvent(messageEventType, dstRegion, dstAgent, dstPlugin);

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }

    private MsgEvent KPIMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }

    private MsgEvent RegionalControllerMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }

    private MsgEvent RegionalAgentMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }

    private MsgEvent RegionalPluginMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }

    private MsgEvent AgentMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
    }


    private MsgEvent PluginMsgEvent(Map<String, String> messageInfo) {
        MsgEvent request = null;
        try {

            MsgEvent.Type messageEventType = MsgEvent.Type.valueOf(messageInfo.get("message_event_type"));
            String dstPlugin = messageInfo.get("dst_plugin");
            request = plugin.getPluginMsgEvent(messageEventType, dstPlugin);

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return request;
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