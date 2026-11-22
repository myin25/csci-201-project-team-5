package edu.usc.team5.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Turns the current row of a ResultSet into one Java value. */
@FunctionalInterface
public interface RowMapper<R> {
    R map(ResultSet rs) throws SQLException;
}
