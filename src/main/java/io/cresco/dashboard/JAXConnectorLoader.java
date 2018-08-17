package io.cresco.dashboard;


import io.cresco.library.plugin.PluginService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;

import java.io.InputStream;

public class JAXConnectorLoader implements Runnable  {

    private BundleContext context;
    private boolean isStarted = false;

    public JAXConnectorLoader(BundleContext context) {
        this.context = context;
    }


    public void run() {

            try {

                boolean isStarted = isPluginStarted();
                if(isStarted) {

/*
                    System.out.println("Starting jersey");
                    String jerseyBundlePath = getClass().getClassLoader().getResource("jersey-all-2.25.1.jar").getPath();
                    InputStream jerseybundleStream = getClass().getClassLoader().getResourceAsStream("jersey-all-2.25.1.jar");
                    context.installBundle(jerseyBundlePath,jerseybundleStream).start();
                    System.out.println("Started jersey");
*/

/*
                    System.out.println("Starting jersey");
                    String jerseyBundlePath = getClass().getClassLoader().getResource("jersey-all-2.22.2.jar").getPath();
                    InputStream jerseybundleStream = getClass().getClassLoader().getResourceAsStream("jersey-all-2.22.2.jar");
                    context.installBundle(jerseyBundlePath,jerseybundleStream).start();
                    System.out.println("Started jersey");
*/

                    //this will expose web pages with jersey
                    String publisherBundlePath = getClass().getClassLoader().getResource("publisher-5.3.1.jar").getPath();
                    InputStream publisherBundleStream = getClass().getClassLoader().getResourceAsStream("publisher-5.3.1.jar");
                    context.installBundle(publisherBundlePath,publisherBundleStream).start();


                }

            } catch(Exception ex) {
                ex.printStackTrace();
            }

    }

    public boolean isPluginStarted() {
        boolean isStarted = false;
        try {
            ServiceReference<?>[] servRefs = null;

            while (servRefs == null) {

                String filterString = "(pluginname=io.cresco.dashboard)";
                Filter filter = context.createFilter(filterString);

                servRefs = context.getServiceReferences(PluginService.class.getName(), filterString);

                if (servRefs == null || servRefs.length == 0) {
                    //System.out.println("NULL FOUND NOTHING!");
                } else {
                    //System.out.println("Running Service Count: " + servRefs.length);

                    for (ServiceReference sr : servRefs) {
                        boolean assign = servRefs[0].isAssignableTo(context.getBundle(), PluginService.class.getName());
                        if(assign) {
                            //System.out.println("Can Assign Service : " + assign);
                            //AgentService as = (AgentService)context.getService(sr);
                            //LoaderService ls = (LoaderService) context.getService(sr);
                        } else {
                            //System.out.println("Can't Assign Service : " + assign);
                        }
                        //Check agent here

                        isStarted = true;

                    }
                }
                Thread.sleep(1000);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return isStarted;
    }




}
