package io.cresco.dashboard.controllers;


import io.cresco.dashboard.Plugin;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;


@Component(service = Object.class,
        scope= ServiceScope.PROTOTYPE,
        property = {
                JaxrsWhiteboardConstants.JAX_RS_APPLICATION_SELECT + "=(osgi.jaxrs.name=.default)",
                JaxrsWhiteboardConstants.JAX_RS_RESOURCE + "=true",
                "dashboard=global"
        },

        reference = @Reference(
                name="io.cresco.dashboard.filters.NotFoundExceptionHandler",
                service=javax.ws.rs.ext.ExceptionMapper.class,
                target="(dashboard=nfx)",
                policy=ReferencePolicy.STATIC
        )
)

public class GlobalController {

    private PluginBuilder plugin;
    private CLogger logger;

    public GlobalController() {

        if(plugin == null) {
            if(Plugin.pluginBuilder != null) {
                plugin = Plugin.pluginBuilder;
                logger = plugin.getLogger(GlobalController.class.getName(), CLogger.Level.Info);
            }
        }
    }

    @GET
    @Path("/dashboard/global/resources")
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
