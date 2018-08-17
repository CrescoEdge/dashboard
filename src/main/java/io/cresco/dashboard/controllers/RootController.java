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
import io.cresco.dashboard.services.AlertService;
import io.cresco.dashboard.services.LoginSessionService;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.plugin.PluginService;
import io.cresco.library.utilities.CLogger;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;

import javax.annotation.security.PermitAll;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
@Component(service = Object.class,
        property="dashboard=root",
        reference = @Reference(
                name="io.cresco.library.plugin.PluginService",
                service=PluginService.class,
                target="(dashboard=core)"
        )
)
*/

@Component(service = Object.class,
        property="dashboard=root",
        reference = @Reference(
                name="java.lang.Object",
                service=Object.class,
                target="(dashboard=nfx)",
                policy=ReferencePolicy.STATIC
        )
)

@Path("/")
//@ApplicationPath("/")
public class RootController {
    //private static PluginBuilder plugin = null;
    //private static CLogger logger = null;
    private PluginBuilder plugin;
    private CLogger logger;

    private static final String LOGIN_ERROR_COOKIE_NAME = "crescoAgentLoginError";
    public static final String LOGIN_REDIRECT_COOKIE_NAME = "crescoAgentLoginRedirect";

    public RootController() {
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
                logger = plugin.getLogger(RootController.class.getName(), CLogger.Level.Info);
            }
        }
    }

    public static void connectPlugin(PluginBuilder inPlugin) {
        //plugin = inPlugin;
        //logger = plugin.getLogger(RootController.class.getName(),CLogger.Level.Info);
        //logger = new CLogger(RootController.class, in_plugin.getMsgOutQueue(), in_plugin.getRegion(), in_plugin.getAgent(), in_plugin.getPluginID(), CLogger.Level.Trace);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {

            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("index.html");

            /*
            Map<String, Object> context = new HashMap<>();
            context.put("user", "admin");
            context.put("section", "root");
            context.put("page", "index");
            */

            Map<String, Object> context = new HashMap<>();
            try {
                context.put("user", loginSession.getUsername());
                context.put("section", "root");
                context.put("page", "index");
            } catch(Exception ex) {
                ex.printStackTrace();
            }

            Writer writer = new StringWriter();
            compiledTemplate.evaluate(writer, context);

            return Response.ok(writer.toString()).build();
        } catch (PebbleException e) {
            return Response.ok("PebbleException: " + e.getMessage()).build();
        } catch (IOException e) {
            return Response.ok("IOException: " + e.getMessage()).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.ok("Server error: " + e.getMessage()).build();
        }
    }

    @PermitAll
    @GET
    @Path("login")
    @Produces(MediaType.TEXT_HTML)
    public Response getLogin(@CookieParam(LOGIN_REDIRECT_COOKIE_NAME) String redirect,
                             @CookieParam(LOGIN_ERROR_COOKIE_NAME) String error) {
        try {
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("login.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("login.mustache");

            Map<String, Object> context = new HashMap<>();
            if (redirect != null)
                context.put("redirect", redirect);
            else
                context.put("redirect", "/services/");
            if (error != null)
                context.put("error", error);

            NewCookie deleteRedirect = new NewCookie(LOGIN_REDIRECT_COOKIE_NAME, null, null, null, null, 0, false);
            NewCookie deleteError = new NewCookie(LOGIN_ERROR_COOKIE_NAME, null, null, null, null, 0, false);

            Writer writer = new StringWriter();
            //compiledTemplate.evaluate(writer, context);
            mustache.execute(writer, context);

            return Response.ok(writer.toString()).cookie(deleteRedirect, deleteError).build();
        } catch (PebbleException e) {
            return Response.ok("PebbleException: " + e.getMessage()).build();
        } /*catch (IOException e) {
            return Response.ok("IOException: " + e.getMessage()).build();
        }*/ catch (Exception e) {
            logger.error("{}", e.getMessage());
            return Response.serverError().build();
        }
    }

    @PermitAll
    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postLogin(@FormParam("username") String username,
                              @FormParam("password") String password,
                              @FormParam("rememberMe") Boolean rememberMe,
                              @FormParam("redirect") String redirect) {
        try {
            if (plugin == null) {
                if (username == null || username.equals("") || !username.toLowerCase().trim().equals("admin") ||
                        password == null || password.equals("") || !password.toLowerCase().trim().equals("cresco")) {
                    NewCookie errorCookie = new NewCookie(LOGIN_ERROR_COOKIE_NAME, "Invalid Username or Password!", null, null, null, 60 * 60, false);
                    return Response.seeOther(new URI("/services/login")).cookie(errorCookie).build();
                }
            } else {
                if (username == null || username.equals("") || !username.toLowerCase().trim().equals(plugin.getConfig().getStringParam("username", "admin").toLowerCase().trim()) ||
                        password == null || password.equals("") || !password.toLowerCase().trim().equals(plugin.getConfig().getStringParam("password", "cresco").toLowerCase().trim())) {
                    NewCookie errorCookie = new NewCookie(LOGIN_ERROR_COOKIE_NAME, "Invalid Username or Password!", null, null, null, 60 * 60, false);
                    return Response.seeOther(new URI("/services/login")).cookie(errorCookie).build();
                }
            }
            LoginSession loginSession = LoginSessionService.create(username.trim(), rememberMe != null);
            return Response.seeOther(new URI(redirect))
                    .cookie(new NewCookie(AuthenticationFilter.SESSION_COOKIE_NAME, loginSession.getId(), null, null, null, 60 * 60 * 24 * 365 * 10, false))
                    .build();
        } catch (URISyntaxException e) {
            return Response.serverError().build();
        }
    }

    @PermitAll
    @GET
    @Path("logout")
    public Response getLogout(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            LoginSessionService.delete(sessionID);
            NewCookie deleteSession = new NewCookie(AuthenticationFilter.SESSION_COOKIE_NAME, null, null, null, null, 0, false);
            return Response.seeOther(new URI("/services/login")).cookie(deleteSession).build();
        } catch (URISyntaxException e) {
            return Response.serverError().build();
        }
    }

    @GET
    @Path("notifications")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            List<Object[]> alerts = AlertService.notifications();
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

    @PermitAll
    @GET
    @Path("/includes/{subResources:.*}")
    //@Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_HTML)
    public Response getIncludes(@PathParam("subResources") String subResources)
    {

        subResources = "/vendors/" + subResources;

        InputStream in = null;
        try{

            in = getClass().getResourceAsStream(subResources);

            if(in == null)
            {

                System.out.println("File NOT FOUND " + subResources);
                //in = getClass().getResourceAsStream("/404.html");
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }
        catch(Exception ex)
        {
            System.out.println(ex.toString());
            //in = getClass().getResourceAsStream("/500.html");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();

        }

        if(subResources.endsWith(".jar"))
        {
            return Response.ok(in, "application/java-archive").build();
        }
        else
        {
            return Response.ok(in, mediaType(subResources)).build();
            //return Response.ok(in, MediaType.TEXT_HTML).build();
            /*
            return Response.ok(in, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + "somefile" + "\"" ) //optional
                    .build();
                    */
        }

    }

    @PermitAll
    @GET
    @Path("/css/{subResources:.*}")
    //@Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_HTML)
    public Response getCSS(@PathParam("subResources") String subResources)
    {

        subResources = "/css/" + subResources;

        InputStream in = null;
        try{

            in = getClass().getResourceAsStream(subResources);

            if(in == null)
            {

                System.out.println("File NOT FOUND " + subResources);
                //in = getClass().getResourceAsStream("/404.html");
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }
        catch(Exception ex)
        {
            System.out.println(ex.toString());
            //in = getClass().getResourceAsStream("/500.html");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();

        }

        if(subResources.endsWith(".jar"))
        {
            return Response.ok(in, "application/java-archive").build();
        }
        else
        {
            return Response.ok(in, mediaType(subResources)).build();
            //return Response.ok(in, MediaType.TEXT_HTML).build();
            /*
            return Response.ok(in, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + "somefile" + "\"" ) //optional
                    .build();
                    */
        }

    }

    @PermitAll
    @GET
    @Path("/js/{subResources:.*}")
    //@Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_HTML)
    public Response getJS(@PathParam("subResources") String subResources)
    {

        subResources = "/js/" + subResources;

        InputStream in = null;
        try{

            in = getClass().getResourceAsStream(subResources);

            if(in == null)
            {

                System.out.println("NOT FOUND!");
                //in = getClass().getResourceAsStream("/404.html");
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }
        catch(Exception ex)
        {
            System.out.println(ex.toString());
            //in = getClass().getResourceAsStream("/500.html");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();

        }

        if(subResources.endsWith(".jar"))
        {
            return Response.ok(in, "application/java-archive").build();
        }
        else
        {
            return Response.ok(in, mediaType(subResources)).build();
            //return Response.ok(in, MediaType.TEXT_HTML).build();
            /*
            return Response.ok(in, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + "somefile" + "\"" ) //optional
                    .build();
                    */
        }

    }


    @PermitAll
    @GET
    @Path("/img/{subResources:.*}")
    //@Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_HTML)
    public Response getImg(@PathParam("subResources") String subResources)
    {

        subResources = "/img/" + subResources;

        InputStream in = null;
        try{

            in = getClass().getResourceAsStream(subResources);

            if(in == null)
            {

                System.out.println("NOT FOUND!");
                //in = getClass().getResourceAsStream("/404.html");
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }
        catch(Exception ex)
        {
            System.out.println(ex.toString());
            //in = getClass().getResourceAsStream("/500.html");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();

        }

        if(subResources.endsWith(".jar"))
        {
            return Response.ok(in, "application/java-archive").build();
        }
        else
        {
            return Response.ok(in, mediaType(subResources)).build();
            //return Response.ok(in, MediaType.TEXT_HTML).build();
            /*
            return Response.ok(in, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + "somefile" + "\"" ) //optional
                    .build();
                    */
        }

    }
    @PermitAll
    @GET
    @Path("/vendors/{subResources:.*}")
    //@Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_HTML)
    public Response getVendors(@PathParam("subResources") String subResources)
    {

        subResources = "/vendors/" + subResources;

        InputStream in = null;
        try{

            in = getClass().getResourceAsStream(subResources);

            if(in == null)
            {

                System.out.println("NOT FOUND!");
                //in = getClass().getResourceAsStream("/404.html");
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }
        catch(Exception ex)
        {
            System.out.println(ex.toString());
            //in = getClass().getResourceAsStream("/500.html");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();

        }

        if(subResources.endsWith(".jar"))
        {
            return Response.ok(in, "application/java-archive").build();
        }
        else
        {
            return Response.ok(in, mediaType(subResources)).build();
            //return Response.ok(in, MediaType.TEXT_HTML).build();
            /*
            return Response.ok(in, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + "somefile" + "\"" ) //optional
                    .build();
                    */
        }

    }

    public String mediaType(String file) {
        int dot = file.lastIndexOf(".");
        if (dot == -1) return MediaType.TEXT_PLAIN;
        String ext = file.substring(dot + 1).toLowerCase();
        switch (ext) {
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "json":
                return MediaType.APPLICATION_JSON;
            case "js":
                return "text/javascript";
            case "css":
                return "text/css";
            case "svg":
                return "image/svg+xml";
                //return MediaType.APPLICATION_SVG_XML;
            case "html":
                return MediaType.TEXT_HTML;
            case "txt":
                return MediaType.TEXT_PLAIN;
            case "jpg":
            case "jpeg":
                return "image/jpg";
            default:
                return MediaType.TEXT_PLAIN;
        }
    }

}
