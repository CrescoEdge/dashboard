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
import io.cresco.library.plugin.PluginService;
import io.cresco.library.utilities.CLogger;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;

import javax.swing.plaf.synth.Region;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;


@Component(service = Object.class,
        property="dashboard=regions",
        reference = @Reference(
                name="java.lang.Object",
                service=Object.class,
                target="(dashboard=nfx)",
                policy=ReferencePolicy.STATIC
        )
)

@Path("regions")
public class RegionsController {
    //private static PluginBuilder plugin = null;
    //private static CLogger logger = null;

    private PluginBuilder plugin;
    private CLogger logger;

    public RegionsController() {

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
                logger = plugin.getLogger(RegionsController.class.getName(), CLogger.Level.Info);
            }
        }
    }

    public static void connectPlugin(PluginBuilder inPlugin) {
        //plugin = inPlugin;
        //logger = plugin.getLogger(RegionsController.class.getName(),CLogger.Level.Info);
        //logger = new CLogger(RegionsController.class, plugin.getMsgOutQueue(), plugin.getRegion(),
        //        plugin.getAgent(), plugin.getPluginID(), CLogger.Level.Trace);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("regions/index.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("regions.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "regions");
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
    @Path("details/{region}")
    @Produces(MediaType.TEXT_HTML)
    public Response details(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID,
                            @PathParam("region") String region) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("regions/index.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("region-details.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "regions");
            context.put("page", "details");
            context.put("region", region);

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
        logger.trace("Call to list()");
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
            request.setParam("action", "listregions");
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String regions = "[]";
            if (response.getParam("regionslist") != null)
                regions = response.getCompressedParam("regionslist");
            return Response.ok(regions, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            if (plugin != null)
                logger.error("list() : {}", sw.toString());
            return Response.ok("{\"regions\":[]}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("resources/{region}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resources(@PathParam("region") String region) {
        logger.trace("Call to resources({})", region);
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
                logger.error("resources({}) : {}", region, sw.toString());
            return Response.ok("{\"regions\":[]}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }
}
