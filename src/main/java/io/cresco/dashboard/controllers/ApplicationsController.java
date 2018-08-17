package io.cresco.dashboard.controllers;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import io.cresco.dashboard.Plugin;
import io.cresco.dashboard.filters.AuthenticationFilter;
import io.cresco.dashboard.models.LoginSession;
import io.cresco.dashboard.services.LoginSessionService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;


@Component(service = Object.class,
        property="dashboard=applications",
        reference = @Reference(
                name="java.lang.Object",
                service=Object.class,
                target="(dashboard=nfx)",
                policy=ReferencePolicy.STATIC
        )
)

@Path("applications")
public class ApplicationsController {
    //private static PluginBuilder plugin = null;
    //private static CLogger logger = null;

    private PluginBuilder plugin;
    private CLogger logger;

    public ApplicationsController() {

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
                logger = plugin.getLogger(ApplicationsController.class.getName(), CLogger.Level.Info);
            }
        }
    }

    public static void connectPlugin(PluginBuilder inPlugin) {
        //plugin = inPlugin;
        //logger = plugin.getLogger(ApplicationsController.class.getName(),CLogger.Level.Info);
        //logger.info("Set STATIC logger : applications");
        //logger = new CLogger(ApplicationsController.class, plugin.getMsgOutQueue(), plugin.getRegion(),
        //        plugin.getAgent(), plugin.getPluginID(), CLogger.Level.Trace);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("applications/list.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("applications.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "applications");
            context.put("page", "index");

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
    @Path("details/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response application(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID,
                                @PathParam("id") String id) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("applications/application.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("application-details.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "applications");
            context.put("page", "application");
            context.put("app_id", id);

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
    @Path("build")
    @Produces(MediaType.TEXT_HTML)
    public Response build(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("applications/build.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("application-builder.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "applications");
            context.put("page", "build");

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
            request.setParam("action", "getgpipelinestatus");

            MsgEvent response = plugin.sendRPC(request);


            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String list = "[]";
            if (response.getParam("pipelineinfo") != null)
                list = response.getCompressedParam("pipelineinfo");
            return Response.ok(list, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("list() : {}", e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @POST
    @Path("add")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response add(@FormParam("tenant_id") String tenant,
                        @FormParam("pipeline") String pipeline) {
        logger.trace("Call to add({}, {})", tenant, pipeline);
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.CONFIG, plugin.getRegion(), plugin.getAgent(),
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
            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.CONFIG);
            request.setParam("action", "gpipelinesubmit");
            request.setParam("action_tenantid", tenant);
            request.setCompressedParam("action_gpipeline", pipeline);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String info = "{}";
            if (response.getParam("gpipeline_id") != null)
                //info = response.getCompressedParam("action_gpipeline");
                info = "{\"gpipeline_id\": \"" + response.getParam("gpipeline_id") + "\"}";

            return Response.ok(info, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("add({}, {}) : {}", tenant, pipeline, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("info/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response info(@PathParam("id") String id) {
        logger.trace("Call to info({})", id);
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
            request.setParam("action", "getgpipeline");
            request.setParam("action_pipelineid", id);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String info = "{}";
            if (response.getParam("gpipeline") != null)
                info = response.getCompressedParam("gpipeline");
            return Response.ok(info, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("info({}) : {}", id, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("nodeinfo/{inode}/{resource}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response nodeInfo(@PathParam("inode") String inode_id,
                             @PathParam("resource") String resource_id) {
        logger.trace("Call to nodeInfo({}, {})", inode_id, resource_id);
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
            request.setParam("action", "getisassignmentinfo");
            request.setParam("action_inodeid", inode_id);
            request.setParam("action_resourceid", resource_id);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            StringBuilder info = new StringBuilder();
            info.append("{");
            info.append("\"isassignmentinfo\":");
            if (response.getParam("isassignmentinfo") != null)
                info.append(response.getCompressedParam("isassignmentinfo"));
            else
                info.append("{}");
            info.append(",");
            info.append("\"isassignmentresourceinfo\":");
            if (response.getParam("isassignmentresourceinfo") != null)
                info.append(response.getCompressedParam("isassignmentresourceinfo"));
            else
                info.append("{}");
            info.append("}");
            return Response.ok(info.toString(), MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("nodeInfo({}) : {}", inode_id, resource_id, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("export/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response export(@PathParam("id") String id) {
        logger.trace("Call to export({})", id);
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
            request.setParam("action", "getgpipelineexport");
            request.setParam("action_pipelineid", id);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String info = "{}";
            if (response.getParam("gpipeline") != null)
                info = response.getCompressedParam("gpipeline");
            return Response.ok(info, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("export({}) : {}", id, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("delete/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id) {
        logger.trace("Call to delete({})", id);
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.CONFIG, plugin.getRegion(), plugin.getAgent(),
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
            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.CONFIG);
            request.setParam("action", "gpipelineremove");
            request.setParam("action_pipelineid", id);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            return Response.seeOther(new URI("/services/applications")).cookie().build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("delete({}) : {}", id, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }
}
