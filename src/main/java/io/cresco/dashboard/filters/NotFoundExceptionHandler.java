package io.cresco.dashboard.filters;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;


@Component(
        scope= ServiceScope.PROTOTYPE,
        property = {
                JaxrsWhiteboardConstants.JAX_RS_APPLICATION_SELECT + "=(osgi.jaxrs.name=.default)",
                JaxrsWhiteboardConstants.JAX_RS_EXTENSION + "=true",
                "dashboard=nfx"
        },
        reference = @Reference(
                name="io.cresco.dashboard.filters.AuthenticationFilter",
                service=javax.ws.rs.container.ContainerRequestFilter.class,
                target="(dashboard=auth)"
        )

)

@Provider
public class NotFoundExceptionHandler implements ExceptionMapper<NotFoundException> {
    @Context
    HttpHeaders headers;

    public Response toResponse(NotFoundException ex) {
        try {
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("error.html");
            Map<String, Object> context = new HashMap<>();
            context.put("errorNo", "404");
            context.put("errorMsg", "Sorry, the page was not found");
            Writer writer = new StringWriter();
            compiledTemplate.evaluate(writer, context);
            return Response.status(404).entity(writer.toString()).build();
        } catch (PebbleException e) {
            return Response.ok("PebbleException: " + e.getMessage()).build();
        } catch (IOException e) {
            return Response.ok("IOException: " + e.getMessage()).build();
        }
    }
}