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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.URL;
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
    private Type type;

    public ShellDataPlane() {

        if(plugin == null) {
            if(Plugin.pluginBuilder != null) {
                plugin = Plugin.pluginBuilder;
                logger = plugin.getLogger(ShellDataPlane.class.getName(), CLogger.Level.Info);
                this.type = new com.google.gson.reflect.TypeToken<Map<String, List<Map<String, String>>>>() {
                }.getType();
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

    private Map<String, String> getExeConfigParams() {
        Map<String, String> configParams = null;
        try {

            configParams = new HashMap<>();

            //determine embedded executor information
            String jarPath = "executor-1.1-SNAPSHOT.jar";
            URL url = getClass().getClassLoader().getResource(jarPath);

            JarInputStream jarInputStream = new JarInputStream(getClass().getClassLoader().getResourceAsStream(jarPath));
            Manifest manifest = jarInputStream.getManifest();

            String MD5 = plugin.getMD5(getClass().getClassLoader().getResourceAsStream(url.getPath()));

            Attributes mainAttributess = manifest.getMainAttributes();

            configParams.put("pluginname", mainAttributess.getValue("Bundle-SymbolicName"));
            configParams.put("version", mainAttributess.getValue("Bundle-Version"));
            configParams.put("md5", MD5);
            configParams.put("jarpath",jarPath);

        } catch (Exception ex) {
            logger.error("getExeConfigParams(): " + ex.getMessage());
        }
        return configParams;
    }

    private boolean execRepoCheck(Map<String, String> configParams) {
        boolean isrepo = false;
        try {

            //check if executor is in global repo
            MsgEvent repo_list_request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.EXEC);
            repo_list_request.setParam("action", "listpluginsrepo");
            MsgEvent repo_list_response = plugin.sendRPC(repo_list_request);
            String outputString = repo_list_response.getCompressedParam("listpluginsrepo");
            Map<String, List<Map<String, String>>> myRepoMap = gson.fromJson(repo_list_response.getCompressedParam("listpluginsrepo"), type);

            for(Map<String,String> pluginMap : myRepoMap.get("plugins")) {
                if((pluginMap.get("pluginname").equals(configParams.get("pluginname"))) && (pluginMap.get("version").equals(configParams.get("version"))) && (pluginMap.get("md5").equals(configParams.get("md5")))) {
                    isrepo = true;
                }
            }
            //{"plugins":[{"pluginname":"io.cresco.executor","jarfile":"68c792d481a2eb2d1d9a02470af3f308",
            // "version":"1.1.0.SNAPSHOT-2022-02-12T145414Z","md5":"68c792d481a2eb2d1d9a02470af3f308"}]}

        } catch (Exception ex) {
            logger.error("execRepoCheck() " + ex.getMessage());
        }
        return isrepo;
    }

    private boolean addExecToRepo(Map<String, String> configParams) {
        boolean isAdded = false;
        try {

            //add if needed
            //byte[] jardata = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource(jarPath).toURI()));
            byte[] jardata = ByteStreams.toByteArray(this.getClass().getClassLoader().getResourceAsStream(configParams.get("jarpath")));

            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.CONFIG);
            request.setParam("action", "savetorepo");
            request.setCompressedParam("configparams", gson.toJson(configParams));
            request.setDataParam("jardata", jardata);
            //request.setCompressedDataParam("jardata", targetArray);

            MsgEvent response = plugin.sendRPC(request);
            if(response.paramsContains("is_saved")) {
                if (Boolean.parseBoolean(response.getParam("is_saved"))) {
                    isAdded = true;
                } else {
                    logger.error("!response.getParam(\"is_saved\") response.getParams(): " + response.getParams().toString());
                }
            } else {
                logger.error("!response.paramsContains(\"is_saved\") response.getParams(): " + response.getParams().toString());
            }

        } catch (Exception ex) {
            logger.error("addExecToRepo: " + ex.getMessage());
        }
        return isAdded;
    }

    private boolean removeExecutor(ShellInfo streamInfo) {

        boolean isRemoved = false;
        try {

            MsgEvent stop_request = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.CONFIG,streamInfo.getRegionId(), streamInfo.getAgentId(), streamInfo.getPluginId());
            stop_request.setParam("action","end_process");
            stop_request.setParam("stream_name", streamInfo.getIdentId());

            MsgEvent stop_response = plugin.sendRPC(stop_request);
            logger.debug("responce from interactive stop: " + stop_response.getParams().toString());

            MsgEvent remove_request = plugin.getGlobalAgentMsgEvent(MsgEvent.Type.CONFIG,streamInfo.getRegionId(), streamInfo.getAgentId());
            remove_request.setParam("action", "pluginremove");
            remove_request.setParam("pluginid",streamInfo.getPluginId());

            MsgEvent remove_response = plugin.sendRPC(remove_request);
            logger.debug("remove_response: " + remove_response.getParams());

            if(remove_response.getParam("status_code").equals("7")) {
                isRemoved = true;
            } else {
                logger.error("removeExecutor error: " + remove_response.getParams());
            }


        } catch (Exception ex) {
            logger.error("removeExecutor: " + ex.getMessage());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.error(sw.toString());
        }

        return isRemoved;
    }

    private ShellInfo initExecutor(ShellInfo streamInfo) {

        try {

            Map<String, String> configParams = getExeConfigParams();

            if(!execRepoCheck(configParams)) {
                logger.debug("Adding plugin to repo");
                if(addExecToRepo(configParams)) {
                    logger.debug("Added plugin to repo");
                } else {
                    logger.error("Failed to add to repo");
                }
            } else {
                logger.debug("plugin found in repo");
            }

            MsgEvent add_request = plugin.getGlobalAgentMsgEvent(MsgEvent.Type.CONFIG,streamInfo.getRegionId(), streamInfo.getAgentId());
            add_request.setParam("action", "pluginadd");
            add_request.setCompressedParam("configparams",gson.toJson(configParams));

            MsgEvent add_response = plugin.sendRPC(add_request);
            logger.debug("add_response: " + add_response.getParams());
            if(add_response.paramsContains("status_code")) {
                logger.debug("STATUS CODE EXISTS: [" + add_response.paramsContains("status_code") + "]");
                logger.debug("GET STATUS CODE: [" + add_response.getParam("status_code") + "]");
                if(add_response.getParam("status_code").equals("10")) {

                    streamInfo.setPluginId(add_response.getParam("pluginid"));

                    MsgEvent config_request = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.CONFIG,streamInfo.getRegionId(), streamInfo.getAgentId(), streamInfo.getPluginId());
                    config_request.setParam("action","config_process");
                    config_request.setParam("stream_name", streamInfo.getIdentId());
                    config_request.setParam("command","-interactive-");
                    MsgEvent config_response = plugin.sendRPC(config_request);
                    logger.debug("responce from interactive config: " + config_response.getParams().toString());

                    MsgEvent start_request = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.CONFIG,streamInfo.getRegionId(), streamInfo.getAgentId(), streamInfo.getPluginId());
                    start_request.setParam("action","start_process");
                    start_request.setParam("stream_name", streamInfo.getIdentId());

                    MsgEvent start_response = plugin.sendRPC(start_request);
                    logger.debug("responce from interactive start: " + start_response.getParams().toString());

                } else {
                    logger.error("!add_request.getParam(\"status_code\").equals(\"10\") status_code= " + add_response.getParam("status_code") );
                }
            } else{
                logger.error("!add_response.paramsContains(\"status_code\") " + add_response.getParams());
            }
            logger.debug("add_responce: " + add_response.getParams().toString());


        } catch (Exception ex) {
            logger.error("initExecutor " + ex.getMessage());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.error(sw.toString());

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
                    logger.debug("DASHBOARD MESSAGE TO EXEC: " + updateMessage.getText());
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
                            logger.debug("DASHBOARD STDOUT INCOMING FROM EXEC: " + ((TextMessage) msg).getText());
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

            streamInfo.setStdoutListenerId(listenerid);

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
                            logger.debug("DASHBOARD STDERR INCOMING FROM EXEC: " + incomingString);
                            logger.debug("Last command: " + latestOutput);
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

            synchronized (lockSessionMap) {
                sessionMap.get(sess.getId()).setStderrListenerId(listenerid);
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

        //remove listeners
        ShellInfo streamInfo;
        synchronized (lockSessionMap) {
            streamInfo = sessionMap.remove(sess.getId());
        }

        if(streamInfo == null) {
            logger.error("onWebSocketClose: why is streamInfo Null?" );
        }

        //remove session
        synchronized (lockSessions) {
            sessions.remove(sess);
        }

        //remove plugin
        if(!removeExecutor(streamInfo)) {
            logger.error("onWebSocketClose: unable to removeExecutor " + streamInfo.getPluginId());
        }


        Plugin.pluginBuilder.getAgentService().getDataPlaneService().removeMessageListener(streamInfo.getStdoutListenerId());
        Plugin.pluginBuilder.getAgentService().getDataPlaneService().removeMessageListener(streamInfo.getStderrListenerId());

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