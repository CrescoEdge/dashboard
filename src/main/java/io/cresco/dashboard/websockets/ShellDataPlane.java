package io.cresco.dashboard.websockets;

import com.google.common.io.ByteStreams;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.cresco.dashboard.Plugin;
import io.cresco.library.data.TopicType;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.jms.Message;
import javax.jms.TextMessage;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

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

    private String latestOutput;

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

        //do some stuff here

        //in = getClass().getResourceAsStream(subResources);

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

    private ShellInfo initExecutor(ShellInfo streamInfo) {

        try {
            //push executor
            //in = getClass().getResourceAsStream(subResources);
            String jarPath = "executor-1.1-SNAPSHOT.jar";
            URL url = getClass().getClassLoader().getResource(jarPath);

            JarInputStream jarInputStream = new JarInputStream(getClass().getClassLoader().getResourceAsStream(jarPath));
            Manifest manifest = jarInputStream.getManifest();

            String MD5 = plugin.getMD5(getClass().getClassLoader().getResourceAsStream(url.getPath()));

            Attributes mainAttributess = manifest.getMainAttributes();

            Map<String, String> configParams = new HashMap<>();
            configParams.put("pluginname", mainAttributess.getValue("Bundle-SymbolicName"));
            configParams.put("version", mainAttributess.getValue("Bundle-Version"));
            configParams.put("md5", MD5);

            //byte[] jardata = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource(jarPath).toURI()));
            byte[] jardata = ByteStreams.toByteArray(this.getClass().getClassLoader().getResourceAsStream(jarPath));

            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.CONFIG);
            request.setParam("action", "savetorepo");
            request.setCompressedParam("configparams", gson.toJson(configParams));
            request.setDataParam("jardata", jardata);
            //request.setCompressedDataParam("jardata", targetArray);

            MsgEvent response = plugin.sendRPC(request);
            if(response.paramsContains("is_saved")) {
                if(Boolean.parseBoolean(response.getParam("is_saved"))) {

                    MsgEvent add_request = plugin.getGlobalAgentMsgEvent(MsgEvent.Type.CONFIG,streamInfo.getRegionId(), streamInfo.getAgentId());
                    add_request.setParam("action", "pluginadd");
                    add_request.setParam("configparams",response.getParam("configparams"));

                    MsgEvent add_response = plugin.sendRPC(add_request);

                    if(add_response.paramsContains("status_code")) {

                        if(add_request.getParam("status_code").equals("10")) {
                            streamInfo.setPluginId(add_request.getParam("pluginid"));

                            MsgEvent config_request = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.CONFIG,streamInfo.getRegionId(), streamInfo.getAgentId(), streamInfo.getPluginId());
                            config_request.setParam("action","config_process");
                            config_request.setParam("stream_name", streamInfo.getIdentId());
                            config_request.setParam("command","-interactive-");
                            MsgEvent config_response = plugin.sendRPC(config_request);
                            logger.error(config_response.getParams().toString());

                            MsgEvent start_request = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.CONFIG,streamInfo.getRegionId(), streamInfo.getAgentId(), streamInfo.getPluginId());
                            start_request.setParam("action","start_process");
                            start_request.setParam("stream_name", streamInfo.getIdentId());

                            MsgEvent start_response = plugin.sendRPC(start_request);
                            logger.error(start_response.getParams().toString());

                        }
                    }
                    logger.error(add_response.getParams().toString());
                }
            }

            logger.error(response.getParams().toString());

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        return streamInfo;
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
                    logger.error("DASHBOARD MESSAGE TO EXEC: " + updateMessage.getText());
                    latestOutput = updateMessage.getText();

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
                //mapMessage.put("region_id","global-region");
                //mapMessage.put("agent_id","global-controller");
                ShellInfo streamInfo = new ShellInfo(sess.getId(),mapMessage.get("ident_key"), mapMessage.get("ident_id"), mapMessage.get("region_id"), mapMessage.get("agent_id"));
                streamInfo.setIoTypeKey(mapMessage.get("io_type_key"));
                streamInfo.setOutputId(mapMessage.get("output_id"));
                streamInfo.setInputId(mapMessage.get("input_id"));


                streamInfo = initExecutor(streamInfo);


                if (createStdoutListener(sess, streamInfo)) {
                    createStderrListener(sess, streamInfo);
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

    private boolean createStdoutListener(Session sess, ShellInfo streamInfo) {
        boolean isCreated = false;
        try{

            javax.jms.MessageListener ml = new javax.jms.MessageListener() {
                public void onMessage(Message msg) {
                    try {


                        if (msg instanceof TextMessage) {
                            logger.error("DASHBOARD STDOUT INCOMING FROM EXEC: " + ((TextMessage) msg).getText());
                            sess.getAsyncRemote().sendObject(((TextMessage) msg).getText());
                        } else {
                            logger.error("Expected Text message");
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

    private boolean createStderrListener(Session sess, ShellInfo streamInfo) {
        boolean isCreated = false;
        try{

            javax.jms.MessageListener ml = new javax.jms.MessageListener() {
                public void onMessage(Message msg) {
                    try {


                        if (msg instanceof TextMessage) {
                            String incomingString = ((TextMessage) msg).getText();
                            logger.error("DASHBOARD STDERR INCOMING FROM EXEC: " + incomingString);
                            logger.error("Last command: " + latestOutput);
                            sess.getAsyncRemote().sendObject(incomingString);
                            /*
                            boolean gobbleLine = false;

                            if(latestOutput != null) {
                                if(incomingString.endsWith(latestOutput)) {
                                    logger.error("MATCHES DON't SEND");
                                    latestOutput = null;
                                    gobbleLine = true;
                                }
                            }

                            if(gobbleLine) {

                            } else {
                                sess.getAsyncRemote().sendObject(incomingString);
                            }

                             */


                        } else {
                            logger.error("Expected Text message");
                        }

                    } catch(Exception ex) {

                        ex.printStackTrace();
                    }
                }
            };

            String stream_query = streamInfo.getIdentKey() + "='" + streamInfo.getIdentId() + "' and " + streamInfo.getIoTypeKey() + "='" + "error" + "'";

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