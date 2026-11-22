package edu.usc.team5.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Closes the connection pool when Tomcat stops or redeploys the app, so connections don't leak. */
@WebListener
public class DbShutdownListener implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        Db.shutdown();
    }
}
