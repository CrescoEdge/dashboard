package io.cresco.dashboard.controllers;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
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
import java.lang.reflect.Type;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

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
        property="dashboard=plugins",
        reference = @Reference(
                name="java.lang.Object",
                service=Object.class,
                target="(dashboard=nfx)",
                policy=ReferencePolicy.STATIC
        )
)

@Path("plugins")
public class PluginsController {
    //private static PluginBuilder plugin = null;
    //private static CLogger logger = null;
    //private static Gson gson;
    private PluginBuilder plugin;
    private CLogger logger;
    private Gson gson;
    private Type type;
    private String tmpDir;

    public PluginsController()  {


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
                logger = plugin.getLogger(PluginsController.class.getName(), CLogger.Level.Info);
                gson = new Gson();
                this.type = new TypeToken<Map<String, List<Map<String, String>>>>() {
                }.getType();
                tmpDir = System.getProperty("java.io.tmpdir");
            }
        }
    }

    public static void connectPlugin(PluginBuilder inPlugin) {
        //plugin = inPlugin;
        //logger = plugin.getLogger(PluginsController.class.getName(),CLogger.Level.Info);
        //logger = new CLogger(PluginsController.class, plugin.getMsgOutQueue(), plugin.getRegion(), plugin.getAgent(),
        //        plugin.getPluginID(), CLogger.Level.Trace);
//        gson = new Gson();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("plugins/index.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("plugins.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "plugins");
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
    @Path("details/{region}/{agent}/{plugin:.*}")
    @Produces(MediaType.TEXT_HTML)
    public Response details(@CookieParam(AuthenticationFilter.SESSION_COOKIE_NAME) String sessionID,
                            @PathParam("region") String region,
                            @PathParam("agent") String agent,
                            @PathParam("plugin") String pluginID) {
        try {
            LoginSession loginSession = LoginSessionService.getByID(sessionID);
            PebbleEngine engine = new PebbleEngine.Builder().build();
            PebbleTemplate compiledTemplate = engine.getTemplate("plugins/index.html");

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("plugin-details.mustache");

            Map<String, Object> context = new HashMap<>();
            if (loginSession != null)
                context.put("user", loginSession.getUsername());
            context.put("section", "plugins");
            context.put("page", "details");
            context.put("region", region);
            context.put("agent", agent);
            context.put("plugin", pluginID);

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
    @Path("info/{region}/{agent}/{plugin:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response info(@PathParam("region") String region,
                         @PathParam("agent") String agent,
                         @PathParam("plugin") String pluginID) {


        logger.trace("Call to info({}, {}, {})", region, agent, pluginID);

        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
            //pluginID = "plugin/" + pluginID;
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
            request.setParam("action", "plugininfo");
            request.setParam("action_region", region);
            request.setParam("action_agent", agent);
            request.setParam("action_plugin", pluginID);
            //logger.error("presend " + request.getParams());
            MsgEvent response = plugin.sendRPC(request);
            //logger.error("postsend " + response.getParams());

            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String info = "{}";
            if (response.getParam("plugininfo") != null)
                info = response.getCompressedParam("plugininfo");
            return Response.ok(info, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("info({}, {}, {}) : {}", region, agent, pluginID, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("kpi/{region}/{agent}/{plugin:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response kpi(@PathParam("region") String region,
                         @PathParam("agent") String agent,
                         @PathParam("plugin") String pluginID) {
        logger.trace("Call to kpi({}, {}, {})", region, agent, pluginID);
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
            request.setParam("action", "pluginkpi");
            request.setParam("action_region", region);
            request.setParam("action_agent", agent);
            request.setParam("action_plugin", pluginID);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String info = "{}";
            if (response.getParam("pluginkpi") != null)
                info = response.getCompressedParam("pluginkpi");
            return Response.ok(info, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("kpi({}, {}, {}) : {}", region, agent, pluginID, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() {
        logger.trace("Call to list()");
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
            request.setParam("action", "listplugins");
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String plugins = "[]";
            if (response.getParam("pluginslist") != null)
                plugins = response.getCompressedParam("pluginslist");
            return Response.ok(plugins, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("list() : {}", e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("list/{region}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listByRegion(@PathParam("region") String region) {
        logger.trace("Call to listByRegion()");
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
            request.setParam("action", "listplugins");
            request.setParam("action_region", region);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String plugins = "[]";
            if (response.getParam("pluginslist") != null)
                plugins = response.getCompressedParam("pluginslist");
            return Response.ok(plugins, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("listByRegion({}) : {}", region, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("list/{region}/{agent}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listByAgent(@PathParam("region") String region,
                                @PathParam("agent") String agent) {
        logger.trace("Call to listByAgent({}, {})", region, agent);
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
            request.setParam("action", "listplugins");
            request.setParam("action_region", region);
            request.setParam("action_agent", agent);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String plugins = "[]";
            if (response.getParam("pluginslist") != null)
                plugins = response.getCompressedParam("pluginslist");
            return Response.ok(plugins, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("listByAgent({}, {}) : {}", region, agent, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    //listpluginsrepo


    @GET
    @Path("listbytype/{id}/{value}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listByPluginType(@PathParam("id") String actionPluginTypeId,
                                @PathParam("value") String actionpluginTypeValue) {
        logger.trace("Call to listByPluginType({}, {})", actionPluginTypeId, actionpluginTypeValue);
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.EXEC, plugin.getRegion(), plugin.getAgent(),
                    plugin.getPluginID(), "Plugin List by Type Request");
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
            request.setParam("action", "listpluginsbytype");
            request.setParam("action_plugintype_id", actionPluginTypeId);
            request.setParam("action_plugintype_value", actionpluginTypeValue);
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String plugins = "[]";
            if (response.getParam("pluginsbytypelist") != null)
                plugins = response.getCompressedParam("pluginsbytypelist");
            return Response.ok(plugins, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("listByPluginType {}, {}) : {}", actionPluginTypeId, actionpluginTypeValue, e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }


    @POST
    @Path("uploadplugin")
    //@Consumes(MediaType.MULTIPART_FORM_DATA)
    @Consumes("application/java-archive")
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFile(
            //FormDataMultiPart formDataMultiPart) {
            //@FormDataParam("file") InputStream uploadedInputStream , @FormDataParam("pluginname") String pluginName, @FormDataParam("jarfile") String jarFile) {
            //@FormDataParam("file") InputStream uploadedInputStream , @FormParam("pluginname") String pluginName, @FormParam("jarfile") String jarFile) {
            InputStream uploadedInputStream) {



        File pluginFileObject = null;
        try {


            String tmpFile = UUID.randomUUID().toString();
            if(tmpDir != null) {
                tmpFile = tmpDir + "/" + tmpFile;
            }

            pluginFileObject = new File(tmpFile);
            if (pluginFileObject.exists()) {
                pluginFileObject.delete();
            }

            saveToFile(uploadedInputStream,tmpFile);

            //if(saveToRepo(uploadedInputStream, tmpFile)) {
            if(saveToRepo(pluginFileObject)) {

            Map<String,String> response = new HashMap<>();
                //response.put("pluginname",pluginName);
                //response.put("jarfile",jarFile);
                //pluginFileObject.delete();
                return Response.ok(gson.toJson(response), MediaType.APPLICATION_JSON_TYPE).build();
            } else {
                return Response.ok("{\"error\":\"Failed to Save File\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            }

        } catch(Exception ex){
            return Response.ok("{\"error\":\"" + ex.toString() + "\"}",
                    MediaType.APPLICATION_JSON_TYPE).build();
        } finally {
            if(pluginFileObject != null) {
                //logger.error("Need to Delete File");
                pluginFileObject.delete();
            }
        }

    }

    private boolean saveToRepo(File tmpJarFile) {
        boolean isSaved = false;
        try {

            String pluginName = plugin.getPluginName(tmpJarFile.getAbsolutePath());
            String pluginMD5 = plugin.getJarMD5(tmpJarFile.getAbsolutePath());
            String pluginJarFile = tmpJarFile.getName();
            String pluginVersion = plugin.getPluginVersion(tmpJarFile.getAbsolutePath());

            if((pluginName != null) && (pluginMD5 != null) && (pluginJarFile != null) && (pluginVersion != null)) {

            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.EXEC);
            request.setParam("action", "listrepoinstances");
            MsgEvent response = plugin.sendRPC(request);

            String region = null;
            String agent = null;
            String pluginID = null;

            if(response != null) {
                if (response.getParams().containsKey("listrepoinstances")) {

                    Map<String, List<Map<String, String>>> myRepoMap = gson.fromJson(response.getCompressedParam("listrepoinstances"), type);
                    if (myRepoMap != null) {

                        if (myRepoMap.containsKey("plugins")) {

                            for (Map<String, String> repoMap : myRepoMap.get("plugins")) {

                                if ((plugin.getRegion().equals(repoMap.get("region"))) && (plugin.getAgent().equals(repoMap.get("agent")))) {
                                    region = repoMap.get("region");
                                    agent = repoMap.get("agent");
                                    pluginID = repoMap.get("pluginid");
                                } else if (plugin.getRegion().equals(repoMap.get("region"))) {
                                    region = repoMap.get("region");
                                    agent = repoMap.get("agent");
                                    pluginID = repoMap.get("pluginid");
                                } else if (region == null) {
                                    region = repoMap.get("region");
                                    agent = repoMap.get("agent");
                                    pluginID = repoMap.get("pluginid");
                                }
                            }
                        }
                    }

                    if ((region != null) && (agent != null) && (pluginID != null)) {

                        java.nio.file.Path jarPath = Paths.get(tmpJarFile.getAbsolutePath());

                        MsgEvent uploadMsg = plugin.getGlobalPluginMsgEvent(MsgEvent.Type.EXEC, region, agent, pluginID);
                        uploadMsg.setParam("action","putjar");
                        uploadMsg.setParam("pluginname", pluginName);
                        uploadMsg.setParam("md5", pluginMD5);
                        uploadMsg.setParam("jarfile", pluginJarFile);
                        uploadMsg.setParam("version", pluginVersion);
                        uploadMsg.setDataParam("jardata", java.nio.file.Files.readAllBytes(jarPath));

                        MsgEvent verify = plugin.sendRPC(uploadMsg);
                        if (verify != null) {
                            if (verify.getParams().containsKey("md5-confirm")) {
                                isSaved = true;
                            }
                        }

                    }

                }
            }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
        return isSaved;
    }

    // save uploaded file to new location
    private boolean saveToFile(InputStream uploadedInputStream,
                            String uploadedFileLocation) {
        boolean isSaved = false;
        try {
            OutputStream out = null;
            int read = 0;
            byte[] bytes = new byte[1024];

            out = new FileOutputStream(new File(uploadedFileLocation));
            while ((read = uploadedInputStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            out.flush();
            out.close();
            isSaved = true;
        } catch (IOException e) {

            e.printStackTrace();
        }
        return isSaved;
    }


    @GET
    @Path("listrepo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listRepo() {
        logger.trace("Call to listRepo()");
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.EXEC, plugin.getRegion(), plugin.getAgent(),
                    plugin.getPluginID(), "Plugin List by Type Request");
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
            request.setParam("action", "listpluginsrepo");
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String plugins = "[]";
            if (response.getParam("listpluginsrepo") != null)
                plugins = response.getCompressedParam("listpluginsrepo");
            return Response.ok(plugins, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("listByPluginType ) : {}", e.getMessage());
            return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("local")
    @Produces(MediaType.APPLICATION_JSON)
    public Response local() {
        try {
            if (plugin == null)
                return Response.ok("{}", MediaType.APPLICATION_JSON_TYPE).build();
            /*
            MsgEvent request = new MsgEvent(MsgEvent.Type.CONFIG, plugin.getRegion(), plugin.getAgent(),
                    plugin.getPluginID(), "Plugin Inventory Request");
            request.setParam("src_region", plugin.getRegion());
            request.setParam("src_agent", plugin.getAgent());
            request.setParam("src_plugin", plugin.getPluginID());
            request.setParam("dst_region", plugin.getRegion());
            request.setParam("dst_agent", plugin.getAgent());
            */
            MsgEvent request = plugin.getGlobalControllerMsgEvent(MsgEvent.Type.EXEC);
            request.setParam("configtype", "plugininventory");
            MsgEvent response = plugin.sendRPC(request);
            if (response == null)
                return Response.ok("{\"error\":\"Cresco rpc response was null\"}",
                        MediaType.APPLICATION_JSON_TYPE).build();
            String plugins = "[]";
            if (response.getParam("pluginlist") != null)
                plugins = response.getParam("pluginlist");
            return Response.ok(plugins, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("list() : {}", e.getMessage());
            return Response.ok("[]", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Path("repository")
    @Produces(MediaType.APPLICATION_JSON)
    public Response repo() {
        try {
            return Response.ok(new Scanner(new URL(plugin.getConfig().getStringParam("plugin_repository_url",
                    "http://128.163.217.124:3446/plugins")).openStream(), "UTF-8")
                    .useDelimiter("\\A").next(), MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            if (plugin != null)
                logger.error("repo() : {}", e.getMessage());
            return Response.ok("[]", MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("add/{json:.*}")
    public Response add(@PathParam("json") String json) {
        JsonElement jElement = new JsonParser().parse(json);
        JsonObject jObject = jElement.getAsJsonObject();
        if (plugin == null)
            return Response.ok("{\"error\":true,\"message\":\"No communication channel open to Cresco Agent.\"}",
                    MediaType.APPLICATION_JSON_TYPE).build();
        logger.info("url: {}, config: {}", jObject.get("url").getAsString(),
                jObject.get("config").getAsString());

        MsgEvent addPlugin = plugin.getAgentMsgEvent(MsgEvent.Type.CONFIG);
        /*
        MsgEvent addPlugin = new MsgEvent(MsgEvent.Type.CONFIG, plugin.getRegion(),
                plugin.getAgent(), plugin.getPluginID(),
                "Issuing command to add new plugin to agent");
        addPlugin.setParam("src_region", plugin.getRegion());
        addPlugin.setParam("src_agent", plugin.getAgent());
        addPlugin.setParam("src_plugin", plugin.getPluginID());
        addPlugin.setParam("dst_region", plugin.getRegion());
        addPlugin.setParam("dst_agent", plugin.getAgent());
        */
        addPlugin.setParam("configtype", "pluginadd");
        addPlugin.setParam("pluginurl", jObject.get("url").getAsString());
        addPlugin.setParam("configparams", jObject.get("config").getAsString());
        /*MsgEvent ret = plugin.sendRPC(addPlugin);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (Map.Entry<String, String> param : ret.getParams().entrySet()) {
            sb.append("\"");
            sb.append(param.getKey());
            sb.append("\":\"");
            sb.append(param.getValue());
            sb.append("\",");
        }
        sb.append("\"error\":false}");*/
        return Response.ok(/*sb.toString()*/"{}", MediaType.APPLICATION_JSON_TYPE).build();
    }
}
