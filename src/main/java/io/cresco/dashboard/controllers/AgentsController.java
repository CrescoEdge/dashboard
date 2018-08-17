package io.cresco.dashboard.controllers;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.gson.Gson;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import io.cresco.dashboard.Activator;
import io.cresco.dashboard.Plugin;
import io.cresco.dashboard.filters.AuthenticationFilter;
import io.cresco.dashboard.models.LoginSession;
import io.cresco.dashboard.services.LoginSessionService;
import io.cresco.library.agent.AgentService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.plugin.PluginService;
import io.cresco.library.utilities.CLogger;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


/*
@Component(service = Object.class,
        reference = @Reference(
                name="java.lang.Object",
                service=Object.class,
                target="(dashboard=root)"
        )
)
*/


@Component(service = Object.class,
        property="dashboard=agents",
        reference = @Reference(
                name="java.lang.Object",
                service=Object.class,
                target="(dashboard=nfx)",
                policy=ReferencePolicy.STATIC
        )
)


@Path("agents")
public class AgentsController {
    private PluginBuilder plugin;
    private CLogger logger;
    //private static Gson gson = gson = new Gson();

    public AgentsController() {

        while(Plugin.pluginBuilder == null) {
            try {
                Thread.sleep(100);
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        }

        if(plugin == null) {
            if(Plugin.pluginBuilder != null) {
                plugin = Plugin.pluginBuilder;
                logger = plugin.getLogger(AgentsController.class.getName(), CLogger.Level.Info);
            }
        }
    }
    public static void connectPlugin(PluginBuilder inPlugin) {
        //plugin = inPlugin;
        //logger = plugin.getLogger(AgentsController.class.getName(), CLogger.Level.Trace);
        //gson = new Gson();


    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("agents/index.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("agents.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "agents");
            context.put("page", "index");

            Writer writer = new StringWriter();
            //compiledTemplate.evaluate(writer, context);
            mustache.execute(writer, context);

            return Response.ok(writer.toString()).build();
        } catch (PebbleException e) {
            e.printStackTrace();
            return Response.ok("PebbleException: " + e.getMessage()).build();
        } /*catch (IOException e) {
            return Response.ok("IOException: " + e.getMessage()).build();
        }*/ catch (Exception e) {
            e.printStackTrace();
            return Response.ok("Server error: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("details/{region}/{agent}")
    @Produces(MediaType.TEXT_HTML)
    public Response details(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID,
                            @PathParam("region") String region,
                            @PathParam("agent") String agent) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("agents/index.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("agent-details.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "agents");
            context.put("page", "details");
            context.put("region", region);
            context.put("agent", agent);

            Writer writer = new StringWriter();
            //compiledTemplate.evaluate(writer, context);
            mustache.execute(writer, context);

            return Response.ok(writer.toString()).build();
        } catch (PebbleException e) {
            return Response.ok("PebbleException: " + e.getMessage()).build();
        } /*catch (IOException e) {
            return Response.ok("IOException: " + e.getMessage()).build();
        }*/ catch (Exception e) {
            return Response.ok("Server error: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() {
        //logger.trace("Call to list()");
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();

            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.EXEC);
            request.setParam("action", "listagents");
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.EXEC, plugin.getRegion(), plugin.getAgent(),
                    plugin.getPluginID(), "Agent List Request");
            request.setParam("src_region", plugin.getRegion());
            request.setParam("src_agent", plugin.getAgent());
            request.setParam("src_plugin", plugin.getPluginID());
            request.setParam("dst_region", plugin.getRegion());
            request.setParam("dst_agent", plugin.getAgent());
            request.setParam("dst_plugin", "plugin/0");
            request.setParam("is_regional", Boolean.TRUE.toString());
            request.setParam("is_global", Boolean.TRUE.toString());
            request.setParam("globalcmd", "true");
            request.setParam("action", "listagents");
            */

            MsgEvent response = plugin.sendRPC(request);

            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String agents = "[]";
            if (response.getParam("agentslist") != null)
                agents = response.getCompressedParam("agentslist");
            return Response.ok(agents, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            /*
            if (plugin != null)
                logger.error("list() : {}", e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
            */
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace();

            if (plugin != null)
                logger.error("list() : {}", sw.toString());
            return Response.ok("{\"agents\":[]}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("listlocal")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listLocal() {
        logger.trace("Call to listLocal()");
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();


            String response = "{\"agents\": [{\"agent\": \""+ plugin.getAgent() +"\",\"region\": \"" + plugin.getRegion() +"\"}]}";

            return Response.ok(response, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("listLocal() : {}", e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("list/{region}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listByRegion(@PathParam("region") String region) {
        logger.trace("Call to listByRegion({})", region);
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.EXEC, plugin.getRegion(), plugin.getAgent(),
                    plugin.getPluginID(), "Agent List Request");
            request.setParam("src_region", plugin.getRegion());
            request.setParam("src_agent", plugin.getAgent());
            request.setParam("src_plugin", plugin.getPluginID());
            request.setParam("dst_region", plugin.getRegion());
            request.setParam("dst_agent", plugin.getAgent());
            request.setParam("dst_plugin", "plugin/0");
            request.setParam("is_regional", Boolean.TRUE.toString());
            request.setParam("is_global", Boolean.TRUE.toString());
            request.setParam("globalcmd", "true");
            */
            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.EXEC);
            request.setParam("action", "listagents");
            request.setParam("action_region", region);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String agents = "[]";
            if (response.getParam("agentslist") != null)
                agents = response.getCompressedParam("agentslist");
            return Response.ok(agents, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("listByRegion({}) : {}", region, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("resources/{region}/{agent}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resources(@PathParam("region") String region,
                              @PathParam("agent") String agent) {
        logger.trace("Call to resources({}, {})", region, agent);
        try {
            if (plugin == null)
                return Response.ok("{\"regions\":[]}", MediaType.APPLICATION_JSON_TYPE).build();
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.EXEC, plugin.getRegion(), plugin.getAgent(),
                    plugin.getPluginID(), "Region List Request");
            request.setParam("src_region", plugin.getRegion());
            request.setParam("src_agent", plugin.getAgent());
            request.setParam("src_plugin", plugin.getPluginID());
            request.setParam("dst_region", plugin.getRegion());
            request.setParam("dst_agent", plugin.getAgent());
            request.setParam("dst_plugin", "plugin/0");
            request.setParam("is_regional", Boolean.TRUE.toString());
            request.setParam("is_global", Boolean.TRUE.toString());
            request.setParam("globalcmd", Boolean.TRUE.toString());
            */
            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.EXEC);
            request.setParam("action", "resourceinfo");
            request.setParam("action_region", region);
            request.setParam("action_agent", agent);
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
                logger.error("resources({}, {}) : {}", region, agent, sw.toString());
            return Response.ok("{\"regions\":[]}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }


    @GET
    @Path("getfreeport/{region}/{agent}/{count}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getfreeport(@PathParam("region") String region,
                                @PathParam("agent") String agent,
                                @PathParam("count") String count){
        logger.trace("Call to resources({}, {})", region, agent);
        try {
            if (plugin == null)
                return Response.ok("{\"ports\":[]}", MediaType.APPLICATION_JSON_TYPE).build();
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.EXEC, plugin.getRegion(), plugin.getAgent(),
                    plugin.getPluginID(), "Region List Request");
            request.setParam("src_region", plugin.getRegion());
            request.setParam("src_agent", plugin.getAgent());
            request.setParam("src_plugin", plugin.getPluginID());
            request.setParam("dst_region", region);
            request.setParam("dst_agent", agent);
            request.setParam("dst_plugin", "plugin/0");
            //request.setParam("is_regional", Boolean.TRUE.toString());
            //request.setParam("is_global", Boolean.TRUE.toString());
            //request.setParam("globalcmd", Boolean.TRUE.toString());
            */
            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.EXEC);
            request.setParam("action", "getfreeports");
            request.setParam("action_portcount", count);
            MsgEvent response = plugin.sendRPC(request);

            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String freeports = "[]";
            if (response.getParam("freeports") != null)
                freeports = response.getCompressedParam("freeports");
            return Response.ok(freeports, MediaType.APPLICATION_JSON_TYPE).build();

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            if (plugin != null)
                logger.error("resources({}, {}) : {}", region, agent, sw.toString());
            return Response.ok("{\"ports\":[]}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }
    /*
    @GET
    @Path("getfreeport/{region}/{agent}/{count}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getfreeport(@PathParam("region") String region,
                              @PathParam("agent") String agent,
                                @PathParam("count") String count){
        logger.trace("Call to resources({}, {})", region, agent);
        try {
            if (plugin == null)
                return Response.ok("{\"ports\":[]}", MediaType.APPLICATION_JSON_TYPE).build();

            //todo this needs to be processed through the global controller no in this plugin
            InetAddress addr = InetAddress.getLocalHost();
            String ip = addr.getHostAddress();
            Map<String,List<Map<String,String>>> portMap = new HashMap<>();
            List<Map<String,String>> portList = new ArrayList<>();

            for(int i = 0; i < Integer.parseInt(count); i++) {
                int port = getPort();
                if(port != -1) {
                    Map<String,String> tmpP = new HashMap<>();
                    tmpP.put("ip",ip);
                    tmpP.put("port",String.valueOf(port));
                    portList.add(tmpP);
                }
            }

            portMap.put("ports",portList);

            String freeports = gson.toJson(portMap);
            return Response.ok(freeports, MediaType.APPLICATION_JSON_TYPE).build();

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            if (plugin != null)
                logger.error("resources({}, {}) : {}", region, agent, sw.toString());
            return Response.ok("{\"ports\":[]}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }
*/
    public int getPort() {

        int freePort = -1;

        boolean isFree = false;

        while (!isFree) {
            int port = ThreadLocalRandom.current().nextInt(10000, 30000 + 1);
            ServerSocket ss = null;
            DatagramSocket ds = null;
            try {
                ss = new ServerSocket(port);
                ss.setReuseAddress(true);
                ds = new DatagramSocket(port);
                ds.setReuseAddress(true);
                isFree = true;
                freePort = port;

            } catch (IOException e) {
            } finally {
                if (ds != null) {
                    ds.close();
                }

                if (ss != null) {
                    try {
                        ss.close();
                    } catch (IOException e) {
                /* should not be thrown */
                    }
                }
            }
        }
        return freePort;
    }

}
