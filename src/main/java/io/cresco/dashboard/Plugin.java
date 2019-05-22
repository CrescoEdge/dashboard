package io.cresco.dashboard;


import io.cresco.dashboard.controllers.*;
import io.cresco.dashboard.filters.AuthenticationFilter;


import io.cresco.dashboard.filters.NotFoundExceptionHandler;
import io.cresco.library.agent.AgentService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.Executor;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.plugin.PluginService;


import io.cresco.library.utilities.CLogger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.*;

import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

@Component(
        service = { PluginService.class },
        scope=ServiceScope.PROTOTYPE,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        servicefactory = true,
        property="dashboard=core",
        reference= { @Reference(name="io.cresco.library.agent.AgentService", service=AgentService.class)}
)

public class Plugin implements PluginService {

    //public PluginBuilder getPluginBuilder() { return  pluginBuilder; }

    public BundleContext context;
    public static PluginBuilder pluginBuilder;
    private Executor executor;
    private CLogger logger;
    //private HttpService server;
    public String repoPath = null;
    private ConfigurationAdmin configurationAdmin;
    private Map<String,Object> map;
    private Server jettyServer;
    private ServletHolder jerseyServlet;

    @Activate
    void activate(BundleContext context, Map<String,Object> map) {

        this.context = context;
        this.map = map;
    }

    @Reference
    protected void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    protected void unsetConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = null;
    }


    @Modified
    void modified(BundleContext context, Map<String,Object> map) {
        System.out.println("Modified Config Map PluginID:" + (String) map.get("pluginID"));
    }

    @Deactivate
    void deactivate(BundleContext context, Map<String,Object> map) {

        isStopped();

        if(this.context != null) {
            this.context = null;
        }

        if(this.map != null) {
            this.map = null;
        }

    }

    @Override
    public boolean inMsg(MsgEvent incoming) {
        pluginBuilder.msgIn(incoming);
        return true;
    }

    private Dictionary<String, String> getJerseyServletParams() {
        Dictionary<String, String> jerseyServletParams = new Hashtable<>();
        jerseyServletParams.put("javax.ws.rs.Application", Plugin.class.getName());
        return jerseyServletParams;
    }

    private String getRepoPath() {
        String path = null;
        try {
            //todo create seperate director for repo
            path = new File(Plugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();

        } catch(Exception ex) {
            //logger.error(ex.getMessage());
            ex.printStackTrace();
        }
        return path;
    }

    @Override
    public boolean isStarted() {
        try {
            //this will be called twice due to JAX-RS-Connector
            if(pluginBuilder == null) {
                pluginBuilder = new PluginBuilder(this.getClass().getName(), context, map);
                this.logger = pluginBuilder.getLogger(Plugin.class.getName(), CLogger.Level.Info);
                this.executor = new PluginExecutor(pluginBuilder);
                pluginBuilder.setExecutor(executor);

                while (!pluginBuilder.getAgentService().getAgentState().isActive()) {
                    logger.info("Plugin " + pluginBuilder.getPluginID() + " waiting on Agent Init");
                    //System.out.println("Plugin " + pluginBuilder.getPluginID() + " waiting on Agent Init");
                    Thread.sleep(1000);
                }

                ResourceConfig rc = new ResourceConfig()
                        .register(AuthenticationFilter.class)
                        .register(NotFoundExceptionHandler.class)
                        .register(RootController.class)
                        .register(AlertsController.class)
                        .register(AgentsController.class)
                        .register(PluginsController.class)
                        .register(RegionsController.class)
                        .register(GlobalController.class)
                        .register(ApplicationsController.class);


                ServletContextHandler context
                        = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");
                jettyServer = new Server(8181);
                jettyServer.setHandler(context);
                jerseyServlet = new ServletHolder(new
                        org.glassfish.jersey.servlet.ServletContainer(rc));
                jerseyServlet.setInitOrder(0);



                context.addServlet(jerseyServlet, "/*");

                try {
                    jettyServer.start();
                    //jettyServer.join();
                } catch (Exception e) {
                   logger.error("Could not start embedded web server");
                    e.printStackTrace();
                }

                pluginBuilder.setIsActive(true);





            }
            return true;

        } catch(Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isStopped() {

        if(jettyServer != null) {
            if(!jettyServer.isStopped()) {
                try {


                    //jettyServer.destroy();
                    //jettyServer.stop();
                } catch (Exception ex) {
                    logger.error("embedded web server shutdown error : " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }

        if(pluginBuilder != null) {
            pluginBuilder.setExecutor(null);
            pluginBuilder.setIsActive(false);
        }
        return true;
    }

}