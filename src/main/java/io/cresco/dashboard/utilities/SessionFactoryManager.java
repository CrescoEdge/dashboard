package io.cresco.dashboard.utilities;

import io.cresco.dashboard.models.Alert;
import io.cresco.dashboard.models.LoginSession;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;

import java.util.HashMap;
import java.util.Map;

public class SessionFactoryManager {
    private static SessionFactory factory;
    private static StandardServiceRegistry registry;

    private static boolean buildSession() {


        // Create registry builder
        StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();

        // Hibernate settings equivalent to hibernate.cfg.xml's properties
        Map<String, String> settings = new HashMap<>();

        try {
            Class.forName("org.h2.Driver");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        settings.put("hibernate.connection.driver_class","org.h2.Driver");
        settings.put("dialect","org.hibernate.dialect.H2Dialect");
        //settings.put("hibernate.connection.url","jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        settings.put("hibernate.connection.url","jdbc:h2:./cresco-data/dashboard-db/h2db;DB_CLOSE_DELAY=-1");

        settings.put(Environment.DRIVER, "org.h2.Driver");
        settings.put(Environment.DIALECT, "org.hibernate.dialect.H2Dialect");

        settings.put("hibernate.c3p0.max_size","20");
        settings.put("hibernate.c3p0.acquire_increment","1");
        settings.put("hibernate.c3p0.idle_test_period","3000");
        settings.put("hibernate.c3p0.max_statements","50");
        settings.put("hibernate.c3p0.timeout","1800");
        settings.put("hibernate.cache.provider_class","org.hibernate.cache.internal.NoCachingRegionFactory");
        settings.put("cache.provider_class","org.hibernate.cache.internal.NoCachingRegionFactory");
        settings.put("hibernate.show_sql","false");
        settings.put("show_sql","false");
        settings.put("hbm2ddl.auto","update");
        settings.put("hibernate.hbm2ddl.auto","update");


        // Apply settings
        registryBuilder.applySettings(settings);

        // Create registry
        registry = registryBuilder.build();

        // Create MetadataSources
        MetadataSources sources = new MetadataSources(registry);

        sources.addAnnotatedClass(Alert.class);
        sources.addAnnotatedClass(LoginSession.class);

        // Create Metadata
        Metadata metadata = sources.getMetadataBuilder().build();

        //<mapping class="io.cresco.dashboard.models.Alert" />
		//<mapping class="io.cresco.dashboard.models.LoginSession" />

        //        metadata.


        try {

            factory = metadata.getSessionFactoryBuilder().build();
            //factory = new MetadataSources( registry ).buildMetadata().buildSessionFactory();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            StandardServiceRegistryBuilder.destroy( registry );
            return false;
        }
    }

    public static Session getSession() {
        if ( factory == null )
            if ( !buildSession() )
                return null;
        return factory.openSession();
    }

    public static void close() {
        if ( factory != null )
            factory.close();
    }
}
