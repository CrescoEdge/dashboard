package io.cresco.dashboard.websockets;

import io.cresco.dashboard.Plugin;
import io.cresco.dashboard.controllers.AgentsController;
import io.cresco.library.data.TopicType;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ClientEndpoint
@ServerEndpoint(value="/dashboard/logstream")
public class LogStreamer
{
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private PluginBuilder plugin;
    private CLogger logger;

    public LogStreamer() {

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
        //System.out.println("Socket Connected: " + sess);
        logger.info("Socket Connected: " + sess);

        MessageListener ml = new MessageListener() {

            public void onMessage(Message msg) {
                try {

                    if (msg instanceof TextMessage) {

                        TextMessage textMessage = (TextMessage)msg;
                        //System.out.println("MESSAGE: [" + textMessage + "]");
                        /*
                        TextMessage textMessage = pluginBuilder.getAgentService().getDataPlaneService().createTextMessage();
                    textMessage.setStringProperty("event","logger");
                    textMessage.setStringProperty("pluginname",pluginBuilder.getConfig().getStringParam("pluginname"));
                    textMessage.setStringProperty("region_id",pluginBuilder.getRegion());
                    textMessage.setStringProperty("agent_id",pluginBuilder.getAgent());
                    textMessage.setStringProperty("plugin_id", pluginBuilder.getPluginID());
                    textMessage.setStringProperty("loglevel", loglevel);
                    textMessage.setText(message);
                         */


                        String messageString = textMessage.getStringProperty("region_id") + "_" + textMessage.getStringProperty("agent_id") + " " + textMessage.getStringProperty("loglevel") + " " + textMessage.getText();
                        sess.getAsyncRemote().sendObject(messageString);
                    }

                } catch(Exception ex) {

                    ex.printStackTrace();
                }
            }
        };

        String DPQuery = "region_id IS NOT NULL AND agent_id IS NOT NULL AND event = 'logger'";
        Plugin.pluginBuilder.getAgentService().getDataPlaneService().addMessageListener(TopicType.AGENT,ml,DPQuery);

    }

    @OnMessage
    public void onWebSocketText(Session sess, String message)
    {
        logger.info("Received TEXT message: " + message);

        String[] sst = message.split(",");
        if(sst.length == 4) {
            String region_id = sst[0];
            String agent_id = sst[1];
            String baseclass = sst[2];
            String loglevel = sst[3];
            MsgEvent req = plugin.getGlobalAgentMsgEvent(MsgEvent.Type.CONFIG, region_id, agent_id);
            req.setParam("action","setloglevel");
            req.setParam("baseclassname", baseclass);
            req.setParam("loglevel", loglevel);

            MsgEvent resp = plugin.sendRPC(req);
            String respMessage = "Error setting loglevel";
            if(resp != null) {
                if(resp.paramsContains("status_code")) {
                    if(resp.getParam("status_code").equals("7")) {
                        respMessage = "set loglevel: " + loglevel + " for baseclass: " + baseclass + " on region_id:" + region_id + " agent_id:" + agent_id;
                    } else {
                        if(resp.paramsContains("status_code")) {
                            respMessage = "could not set loglevel status_code: " + resp.getParam("status_code") + " status_desc: " + resp.getParam("status_desc");
                        } else {
                            respMessage = "could not set loglevel status_code: " + resp.getParam("status_code");
                        }
                    }
                }
            }
            sess.getAsyncRemote().sendObject(respMessage);

        }

    }

    @OnClose
    public void onWebSocketClose(Session sess, CloseReason reason)
    {
        logger.info("Socket Closed: " + reason);
        //System.out.println("Socket Closed: " + reason);
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