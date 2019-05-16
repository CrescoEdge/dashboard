package io.cresco.dashboard.controllers;


import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.cresco.dashboard.Plugin;
import io.cresco.library.data.TopicType;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.jms.TextMessage;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Map;


@Path("/dashboard/global")
public class GlobalController {

    private PluginBuilder plugin;
    private CLogger logger;
    private Gson gson;

    public GlobalController() {

        if(plugin == null) {
            if(Plugin.pluginBuilder != null) {
                plugin = Plugin.pluginBuilder;
                logger = plugin.getLogger(GlobalController.class.getName(), CLogger.Level.Info);
                gson = new Gson();
            }
        }
    }

    @POST
    @Path("/cdp")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response cdp(@FormParam("action_id") String actionId,
                        @FormParam("message") String message) {
        logger.trace("Call to cdp({}, {})", actionId, message);
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();

            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> myMap = gson.fromJson(message,type);

            String region = myMap.get("region_id");
            String agent = myMap.get("agent_id");
            String pluginId = myMap.get("plugin_id");

            MsgEvent response = null;


            switch (actionId) {
                case "create":

                    MsgEvent createQuery = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.CONFIG, region, agent, pluginId);
                    createQuery.setParam("action", "queryadd");

                    createQuery.setCompressedParam("input_schema", myMap.get("input_schema"));
                    createQuery.setParam("input_stream_name", myMap.get("input_stream_name"));
                    createQuery.setParam("output_stream_name", myMap.get("output_stream_name"));
                    createQuery.setParam("output_stream_attributes", myMap.get("output_stream_attributes"));
                    createQuery.setParam("query_id", myMap.get("query_id"));
                    createQuery.setParam("query", myMap.get("query"));
                    createQuery.setCompressedParam("output_list",myMap.get("output_list"));
                    //createQuery.setParam("output_agent",myMap.get("output_agent"));
                    //createQuery.setParam("output_plugin",myMap.get("output_plugin"));

                    //logger.error(createQuery.getParams().toString());

                    response = plugin.sendRPC(createQuery);

                    if (response != null) {
                        if(response.getParam("output_schema") != null) {
                            return Response.ok(response.getParam("output_schema"), MediaType.APPLICATION_JSON_TYPE).build();
                        }
                    }

                    return Response.ok("{\"error\":\"Some Create Error.\"}",
                            MediaType.APPLICATION_JSON_TYPE).build();

                case "delete":

                    MsgEvent deleteQuery = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.CONFIG, region, agent, pluginId);
                    deleteQuery.setParam("action", "querydel");
                    deleteQuery.setParam("query_id", myMap.get("query_id"));

                    response = plugin.sendRPC(deleteQuery);

                    if (response != null) {
                        if(response.getParam("iscleared") != null) {
                            return Response.ok(response.getParam("iscleared"), MediaType.APPLICATION_JSON_TYPE).build();
                        }
                    }

                    return Response.ok("{\"error\":\"Some Delete Error.\"}",
                            MediaType.APPLICATION_JSON_TYPE).build();

                case "input":

                    MsgEvent inputMsg = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.EXEC, region, agent, pluginId);
                    inputMsg.setParam("action", "queryinput");
                    inputMsg.setParam("query_id", myMap.get("query_id"));
                    inputMsg.setParam("input_stream_name", myMap.get("input_stream_name"));
                    inputMsg.setCompressedParam("input_stream_payload", myMap.get("input_stream_payload"));

                    plugin.msgOut(inputMsg);

                    return Response.ok("{\"input\":\"true\"}",
                            MediaType.APPLICATION_JSON_TYPE).build();

                case "input_stream":

                    TextMessage tm = plugin.getAgentService().getDataPlaneService().createTextMessage();
                    tm.setStringProperty("stream_name",myMap.get("input_stream_name"));
                    tm.setText(myMap.get("input_stream_payload"));
                    plugin.getAgentService().getDataPlaneService().sendMessage(TopicType.AGENT,tm);


                    /*
                    MsgEvent inputStreamMsg = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.EXEC, region, agent, pluginId);
                    inputStreamMsg.setParam("action", "queryinput");
                    inputStreamMsg.setParam("query_id", myMap.get("query_id"));
                    inputStreamMsg.setParam("input_stream_name", myMap.get("input_stream_name"));
                    inputStreamMsg.setCompressedParam("input_stream_payload", myMap.get("input_stream_payload"));

                    plugin.msgOut(inputStreamMsg);
                    */

                    return Response.ok("{\"input\":\"true\"}",
                            MediaType.APPLICATION_JSON_TYPE).build();

                default:
                    logger.error("Unknown action found: {} {}", actionId, message);

            }

            String info = "{}";
            return Response.ok(info, MediaType.APPLICATION_JSON_TYPE).build();

        } catch (Exception e) {
            if (plugin != null)
                logger.error("cdp({}, {}) : {}", actionId, message, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("/resources")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resources() {
        logger.trace("Call to resources()");
        try {
            if (plugin == null)
                return Response.ok("{\"regions\":[]}", MediaType.APPLICATION_JSON_TYPE).build();

            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.EXEC);
            request.setParam("action", "resourceinfo");
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String regions = "[]";
            if (response.getParam("resourceinfo") != null)
                regions = response.getCompressedParam("resourceinfo");
            return Response.ok(regions, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            if (plugin != null)
                logger.error("resources() : {}", sw.toString());
            return Response.ok("{\"error\":\"" + e.getMessage() + "\"}",
                    MediaType.APPLICATION_JSON_TYPE).build();
        }
    }


}
