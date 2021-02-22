package io.cresco.dashboard.controllers;

import io.cresco.dashboard.Plugin;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;


@Path("/dashboard/api")
public class APIController {
    private PluginBuilder plugin;
    private CLogger logger;

    public APIController() {

        if (plugin == null) {
            if (Plugin.pluginBuilder != null) {
                plugin = Plugin.pluginBuilder;
                logger = plugin.getLogger(APIController.class.getName(), CLogger.Level.Info);
            }
        }
    }

    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getlog() {
        try {
                return Response.ok("{\"version\":\"" + plugin.getConfig().getStringParam("version") + "\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

                return Response.ok("{\"error\":\"something went wrong\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
        }

    }

}
