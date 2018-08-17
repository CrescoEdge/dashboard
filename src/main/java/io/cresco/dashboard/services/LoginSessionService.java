package io.cresco.dashboard.services;

import io.cresco.dashboard.models.LoginSession;
import io.cresco.dashboard.utilities.SessionFactoryManager;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.resource.transaction.spi.TransactionStatus;

import java.util.Date;

public class LoginSessionService {
    public static synchronized LoginSession create(String username, Boolean rememberMe) {
        Session session = SessionFactoryManager.getSession();
        if (session == null)
            return null;
        try {
            session.getTransaction().begin();
            LoginSession object = new LoginSession( username, rememberMe );
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

    public static LoginSession getByID(String id) {
        Session session = SessionFactoryManager.getSession();
        if (session == null) {
            return null;
        }
        try {
            session.getTransaction().begin();
            Query query = session.createQuery( "from LoginSession where id = :id" );
            query.setString("id", id);
            LoginSession object = (LoginSession) query.uniqueResult();
            session.getTransaction().commit();
            return object;
        } catch (Exception e) {
            if (session.getTransaction().getStatus() == TransactionStatus.ACTIVE ||
                    session.getTransaction().getStatus() == TransactionStatus.MARKED_ROLLBACK)
                session.getTransaction().rollback();
            e.printStackTrace();
            return null;
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
        Session session = SessionFactoryManager.getSession();
        if (session == null)
            return;
        try {
            session.getTransaction().begin();
            Query query = session.createQuery( "from LoginSession where id = :id" );
            query.setString("id", id);
            LoginSession object = (LoginSession) query.uniqueResult();
            if (object == null)
                return;
            session.delete( object );
            session.getTransaction().commit();
        } catch (Exception e) {
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

    public static LoginSession seen(LoginSession object) {
        Session session = SessionFactoryManager.getSession();
        if (session == null)
            return null;
        try {
            session.getTransaction().begin();
            object.setLastSeen(new Date().getTime());
            session.update( object );
            session.getTransaction().commit();
            return object;
        } catch (RuntimeException e) {
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
}
