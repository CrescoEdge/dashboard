package io.cresco.dashboard.utilities;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

public class SessionFactoryManager {
    private static SessionFactory factory;

    private static boolean buildSession() {
        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .configure()
                .loadProperties("db.properties")
                .build();
        try {
            factory = new MetadataSources( registry ).buildMetadata().buildSessionFactory();
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
