package io.cresco.dashboard.websockets;

import io.cresco.dashboard.Plugin;
import io.cresco.dashboard.controllers.AgentsController;
import io.cresco.library.data.TopicType;
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
@ServerEndpoint(value="/dashboard/logstream/")
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
                        String location = null;
                        if(textMessage.getStringProperty("plugin_id") != null) {
                            location = textMessage.getStringProperty("region_id") + "_" + textMessage.getStringProperty("agent_id") + "_" + textMessage.getStringProperty("plugin_id");
                        } else {
                            location = textMessage.getStringProperty("region_id") + "_" + textMessage.getStringProperty("agent_id");
                        }

                        String messageString = location + " " + textMessage.getStringProperty("loglevel") + " " + textMessage.getText();
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
    public void onWebSocketText(String message)
    {
        logger.info("Received TEXT message: " + message);
        //System.out.println("Received TEXT message: " + message);
        //broadcast(message);

        /*
        try {
            TextMessage textMessage = Plugin.pluginBuilder.getAgentService().getDataPlaneService().createTextMessage();
            textMessage.setText("FROM QUEUE: " + message);
            Plugin.pluginBuilder.getAgentService().getDataPlaneService().sendMessage(TopicType.AGENT, textMessage);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
         */
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