package io.cresco.dashboard.controllers;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import io.cresco.dashboard.Plugin;
import io.cresco.dashboard.filters.AuthenticationFilter;
import io.cresco.dashboard.models.Alert;
import io.cresco.dashboard.models.LoginSession;
import io.cresco.dashboard.services.AlertService;
import io.cresco.dashboard.services.LoginSessionService;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.plugin.PluginService;
import io.cresco.library.utilities.CLogger;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component(service = Object.class,
        scope= ServiceScope.PROTOTYPE,
        property = {
                JaxrsWhiteboardConstants.JAX_RS_APPLICATION_SELECT + "=(osgi.jaxrs.name=.default)",
                JaxrsWhiteboardConstants.JAX_RS_RESOURCE + "=true",
                "dashboard=alerts"
        },

        reference = @Reference(
                name="io.cresco.dashboard.filters.NotFoundExceptionHandler",
                service=javax.ws.rs.ext.ExceptionMapper.class,
                target="(dashboard=nfx)",
                policy=ReferencePolicy.STATIC
        )
)

public class AlertsController {
    private PluginBuilder plugin = null;
    private CLogger logger = null;

    public AlertsController() {


        if(plugin == null) {
            if(Plugin.pluginBuilder != null) {
                plugin = Plugin.pluginBuilder;
                logger = plugin.getLogger(AlertsController.class.getName(), CLogger.Level.Info);
            }
        }
    }


    @GET
    @Path("/dashboard/alerts/")
    @Produces(MediaType.TEXT_HTML)
    public Response index(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("alerts/index.html");

            Map<String, Object> context = new HashMap<>();
            context.put("user", loginSession.getUsername());
            context.put("section", "alerts");
            context.put("page", "index");

            Writer writer = new StringWriter();
            compiledTemplate.evaluate(writer, context);

            return Response.ok(writer.toString()).build();
        } catch (PebbleException e) {
            return Response.ok("PebbleException: " + e.getMessage()).build();
        } catch (IOException e) {
            return Response.ok("IOException: " + e.getMessage()).build();
        } catch (Exception e) {
            return Response.ok("Server error: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/dashboard/alerts/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            List<Object[]> alerts = AlertService.all();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (Object[] alert : alerts) {
                sb.append("{\"id\":\"");
                sb.append(alert[0]);
                sb.append("\",");

                sb.append("\"created\":");
                sb.append(alert[1]);
                sb.append(",");

                sb.append("\"msg\":\"");
                sb.append(alert[2]);
                sb.append("\"},");
            }
            sb.append("]");
            if (alerts.size() > 0)
                sb.deleteCharAt(sb.lastIndexOf(","));
            return Response.ok(sb.toString(), MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("list() : {}", e.getMessage());
            return Response.ok("[]", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }
}
