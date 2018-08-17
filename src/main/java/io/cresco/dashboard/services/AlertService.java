package io.cresco.dashboard.services;

import io.cresco.dashboard.models.Alert;
import io.cresco.dashboard.utilities.SessionFactoryManager;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.resource.transaction.spi.TransactionStatus;

import java.util.ArrayList;
import java.util.List;

public class AlertService {
    public static synchronized Alert create(String message) {
        Session session = SessionFactoryManager.getSession();
        if (session == null)
            return null;
        try {
            session.getTransaction().begin();
            Alert object = new Alert( message );
            session.save( object );
            session.getTransaction().commit();
            return object;
        } catch (Exception e) {
            if (session.getTransaction().getStatus() == TransactionStatus.ACTIVE ||
                    session.getTransaction().getStatus() == TransactionStatus.MARKED_ROLLBACK)
                session.getTransaction().rollback();
            return null;
        } finally {
            try {
                session.close();
            } catch (HibernateException e) {
                e.printStackTrace();
            }
        }
    }

    public static Alert getById(String id) {
        Session session = SessionFactoryManager.getSession();
        if (session == null)
            return null;
        try {
            session.getTransaction().begin();
            Query query = session.createQuery( "from Alert where id = :id" );
            query.setString("id", id);
            Alert object = (Alert) query.uniqueResult();
            session.getTransaction().commit();
            return object;
        } catch (Exception e) {
            if (session.getTransaction().getStatus() == TransactionStatus.ACTIVE ||
                    session.getTransaction().getStatus() == TransactionStatus.MARKED_ROLLBACK)
                session.getTransaction().rollback();
            return null;
        } finally {
            try {
                session.close();
            } catch (HibernateException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Object[]> all() {
        Session session = SessionFactoryManager.getSession();
        if (session == null)
            return new ArrayList<>();
        try {
            session.getTransaction().begin();
            Query query = session.createSQLQuery( "select id, created, message from Alert" );
            final List list = query.list();
            session.getTransaction().commit();
            return list;
        } catch (RuntimeException e) {
            if (session.getTransaction().getStatus() == TransactionStatus.ACTIVE ||
                    session.getTransaction().getStatus() == TransactionStatus.MARKED_ROLLBACK)
                session.getTransaction().rollback();
            return new ArrayList<>();
        } finally {
            try {
                session.close();
            } catch (HibernateException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Object[]> notifications() {
        Session session = SessionFactoryManager.getSession();
        if (session == null)
            return new ArrayList<>();
        try {
            session.getTransaction().begin();
            Query query = session.createSQLQuery( "select id, created, message from Alert order by created desc limit 5" );
            final List list = query.list();
            session.getTransaction().commit();
            return list;
        } catch (RuntimeException e) {
            if (session.getTransaction().getStatus() == TransactionStatus.ACTIVE ||
                    session.getTransaction().getStatus() == TransactionStatus.MARKED_ROLLBACK)
                session.getTransaction().rollback();
            return new ArrayList<>();
        } finally {
            try {
                session.close();
            } catch (HibernateException e) {
                e.printStackTrace();
            }
        }
    }

    public static void delete(String id) {
        if (id == null)
            return;
        Alert object = getById(id);
        if (object == null)
            return;
        Session session = SessionFactoryManager.getSession();
        if (session == null)
            return;
        try {
            session.getTransaction().begin();
            session.delete( object );
            session.getTransaction().commit();
        } catch (RuntimeException e) {
            if (session.getTransaction().getStatus() == TransactionStatus.ACTIVE ||
                    session.getTransaction().getStatus() == TransactionStatus.MARKED_ROLLBACK)
                session.getTransaction().rollback();
        } finally {
            try {
                session.close();
            } catch (HibernateException e) {
                e.printStackTrace();
            }
        }
    }
}
