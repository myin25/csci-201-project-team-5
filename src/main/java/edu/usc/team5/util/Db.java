package edu.usc.team5.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Shared database connection pool (HikariCP).
 *
 * Opening a new database connection is slow, so the pool keeps a few open and lends them out.
 * Always use try-with-resources so the connection goes back to the pool:
 *
 * <pre>
 * try (Connection c = Db.getConnection()) { ... }
 * </pre>
 *
 * For several statements that must succeed or fail together (placing a bid, closing an
 * auction), use {@link #inTransaction(SqlWork)}.
 *
 * Settings come from environment variables DB_URL, DB_USERNAME, DB_PASSWORD (production on
 * Vercel/Supabase) or, if those are missing, from src/main/resources/db.properties (local).
 * The same code therefore runs against MySQL locally and Postgres in production.
 */
public final class Db {

    private static volatile HikariDataSource pool;

    private Db() {}

    /** Borrows a connection from the pool. Close it (try-with-resources) to return it. */
    public static Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    /**
     * Runs {@code work} inside one transaction: commits if it returns normally, rolls back
     * every change if it throws. Returns whatever {@code work} returns (use null if nothing).
     *
     * <pre>
     * BidResult r = Db.inTransaction(conn -> {
     *     int updated = listingDao.conditionalRaise(conn, listingId, bidderId, amount);
     *     if (updated == 0) return BidResult.TOO_LOW;   // nothing written, still commits cleanly
     *     bidDao.insert(conn, bid);
     *     return BidResult.ACCEPTED;
     * });
     * </pre>
     */
    public static <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection conn = getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /** Closes the pool. Called once when the web app shuts down (see DbShutdownListener). */
    public static synchronized void shutdown() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    private static HikariDataSource pool() {
        HikariDataSource p = pool;
        if (p == null) {
            synchronized (Db.class) {
                p = pool;
                if (p == null) {
                    p = createPool();
                    pool = p;
                }
            }
        }
        return p;
    }

    private static HikariDataSource createPool() {
        Properties file = loadPropertiesFile();
        String url = setting("DB_URL", "db.url", file);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                "No database configured. Copy src/main/resources/db.properties.example to "
                + "db.properties and fill it in, or set the DB_URL environment variable.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(setting("DB_USERNAME", "db.username", file));
        config.setPassword(setting("DB_PASSWORD", "db.password", file));
        // Tomcat loads web apps in their own class loader, so name the driver explicitly
        // instead of relying on JDBC auto-discovery.
        if (url.startsWith("jdbc:mysql:")) {
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else if (url.startsWith("jdbc:postgresql:")) {
            config.setDriverClassName("org.postgresql.Driver");
        }
        config.setMaximumPoolSize(10);
        config.setPoolName("ticket-auction");
        return new HikariDataSource(config);
    }

    private static String setting(String envName, String propertyName, Properties file) {
        String env = System.getenv(envName);
        return (env != null && !env.isBlank()) ? env : file.getProperty(propertyName);
    }

    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        try (InputStream in = Db.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read db.properties", e);
        }
        return props;
    }
}
