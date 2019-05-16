package io.cresco.dashboard.filters;


import io.cresco.dashboard.Plugin;
import io.cresco.dashboard.controllers.RootController;
import io.cresco.dashboard.models.LoginSession;
import io.cresco.dashboard.services.LoginSessionService;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.annotation.security.PermitAll;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;

@Provider
public class AuthenticationFilter implements ContainerRequestFilter {
    public static final String SESSION_COOKIE_NAME = "crescoAgentSessionID";
    private static final int TIMEOUT_IN_MINUTES = 120;
    private static Response REDIRECT_LOGOUT;
    //private static CLogger logger;

    private static PluginBuilder plugin;
    private static CLogger logger;


    @Context
    private ResourceInfo resourceInfo;

    public AuthenticationFilter() {

        try {

            if(plugin == null) {
                if(Plugin.pluginBuilder != null) {
                    plugin = Plugin.pluginBuilder;
                    logger = plugin.getLogger(AuthenticationFilter.class.getName(), CLogger.Level.Info);
                }
            }

            URI logout_uri = new URI("/dashboard/logout");
            REDIRECT_LOGOUT = Response.seeOther(logout_uri).build();


        } catch (URISyntaxException e) {
            REDIRECT_LOGOUT = Response.serverError().build();
        }
    }

    public static void connectPlugin(PluginBuilder pluginBuilder) {
        plugin = pluginBuilder;
        logger = plugin.getLogger(AuthenticationFilter.class.getName(), CLogger.Level.Info);

    }

    private Response toLogin(NewCookie redirectCookie) {
        try {
            URI login_uri = new URI("/dashboard/login");
            return Response.seeOther(login_uri).cookie(redirectCookie).build();
        } catch (URISyntaxException e) {
            return Response.serverError().build();
        }
    }


    @Override
    public void filter(ContainerRequestContext requestContext) {
        try {


            //don't bother if not the dashboard
            if(!requestContext.getUriInfo().getPath().startsWith("dashboard")) {
                return;
            }

            Method method = resourceInfo.getResourceMethod();


            if (method.isAnnotationPresent(PermitAll.class)) {
                return;
            }

            // Then check is the service key exists and is valid.
            String serviceKey = requestContext.getHeaderString("X-Auth-API-Service-Key");
            if (serviceKey != null) {
                return;
            }

            Cookie sessionCookie = requestContext.getCookies().get(SESSION_COOKIE_NAME);
            if (sessionCookie == null) {
                NewCookie redirectCookie = new NewCookie(RootController.LOGIN_REDIRECT_COOKIE_NAME, "/" + requestContext.getUriInfo().getPath(), null, null, null, 60 * 60, false);
                requestContext.abortWith(toLogin(redirectCookie));
                return;
            }

            LoginSession loginSession = LoginSessionService.getByID(sessionCookie.getValue());
            if (loginSession == null) {
                requestContext.abortWith(REDIRECT_LOGOUT);
                return;
            }
            Calendar timeout = Calendar.getInstance();
            timeout.add(Calendar.MINUTE, -1 * TIMEOUT_IN_MINUTES);
            if (loginSession.getLastSeenAsDate().before(timeout.getTime()) && !loginSession.getRemememberMe()) {
                requestContext.abortWith(REDIRECT_LOGOUT);
                return;
            }
            LoginSessionService.seen(loginSession);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
