package edu.usc.team5.util;

import java.sql.Connection;
import java.sql.SQLException;

/** A block of database work that runs on one connection. Used by {@link Db#inTransaction}. */
@FunctionalInterface
public interface SqlWork<T> {
    T run(Connection conn) throws SQLException;
}
