package edu.usc.team5.dao;

import edu.usc.team5.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parent of every DAO (UserDAO, ListingDAO, BidDAO, ...). DAOs are the only classes that run SQL.
 *
 * A child DAO passes its table name and primary-key column to the constructor and implements
 * {@link #mapRow(ResultSet)}. Everything else (borrowing a connection, binding parameters,
 * closing statements, reading results) is handled here.
 *
 * <pre>
 * public class GameDAO extends BaseDAO&lt;Game&gt; {
 *     public GameDAO() { super("Games", "game_id"); }
 *
 *     protected Game mapRow(ResultSet rs) throws SQLException {
 *         return new Game(rs.getInt("game_id"), rs.getString("opponent"),
 *                         getInstant(rs, "game_date"), rs.getString("location"));
 *     }
 *
 *     public List&lt;Game&gt; findUpcoming(Instant from) throws SQLException {
 *         return queryList("SELECT * FROM Games WHERE game_date &gt;= ? ORDER BY game_date", from);
 *     }
 * }
 * </pre>
 *
 * Rules for every DAO:
 * <ul>
 *   <li>Always use ? placeholders and pass values as parameters. Never build SQL by
 *       concatenating user input (SQL injection).</li>
 *   <li>Each helper has two versions. The one without a Connection borrows and returns its own.
 *       The one that takes a Connection is for work inside {@link Db#inTransaction}.</li>
 *   <li>Pass times as {@link Instant} (from {@code Clock.now()}), not UTC_TIMESTAMP() or now(),
 *       so the same SQL runs on MySQL and Postgres.</li>
 *   <li>Write table names exactly as in db/mysql/schema.sql (Users, not users): MySQL on Linux
 *       is case-sensitive about table names.</li>
 * </ul>
 */
public abstract class BaseDAO<T> {

    private final String table;
    private final String idColumn;

    /**
     * @param table    table name exactly as in the schema, e.g. "Listings"
     * @param idColumn primary-key column, e.g. "listing_id"
     */
    protected BaseDAO(String table, String idColumn) {
        this.table = table;
        this.idColumn = idColumn;
    }

    /** Turns the current row into a model object. Read columns by name, not position. */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    /** Borrows a pooled connection. Use try-with-resources. */
    protected Connection getConnection() throws SQLException {
        return Db.getConnection();
    }

    // ---------------------------------------------------------------- find by id

    public Optional<T> findById(long id) throws SQLException {
        try (Connection conn = getConnection()) {
            return findById(conn, id);
        }
    }

    public Optional<T> findById(Connection conn, long id) throws SQLException {
        return queryOne(conn, "SELECT * FROM " + table + " WHERE " + idColumn + " = ?", id);
    }

    // ---------------------------------------------------------------- queries returning T

    /** Runs a SELECT and maps every row with {@link #mapRow}. */
    protected List<T> queryList(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection()) {
            return queryList(conn, sql, params);
        }
    }

    protected List<T> queryList(Connection conn, String sql, Object... params) throws SQLException {
        return query(conn, sql, this::mapRow, params);
    }

    /** Runs a SELECT expected to match at most one row. */
    protected Optional<T> queryOne(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection()) {
            return queryOne(conn, sql, params);
        }
    }

    protected Optional<T> queryOne(Connection conn, String sql, Object... params) throws SQLException {
        List<T> rows = queryList(conn, sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // ---------------------------------------------------------------- queries returning anything

    /**
     * Runs a SELECT and maps each row with your own mapper, for results that aren't a T,
     * e.g. a list of ids: {@code query(conn, "SELECT listing_id FROM ...", rs -> rs.getInt(1), now)}.
     */
    protected <R> List<R> query(Connection conn, String sql, RowMapper<R> mapper, Object... params)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<R> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        }
    }

    /** Runs a query whose first column is a number, e.g. SELECT COUNT(*). Returns 0 if no rows. */
    protected long queryLong(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection()) {
            return queryLong(conn, sql, params);
        }
    }

    protected long queryLong(Connection conn, String sql, Object... params) throws SQLException {
        List<Long> values = query(conn, sql, rs -> rs.getLong(1), params);
        return values.isEmpty() ? 0 : values.get(0);
    }

    // ---------------------------------------------------------------- writes

    /** Runs an UPDATE or DELETE. Returns how many rows changed. */
    protected int update(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection()) {
            return update(conn, sql, params);
        }
    }

    protected int update(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** Runs an INSERT and returns the new row's auto-generated id. */
    protected long insert(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection()) {
            return insert(conn, sql, params);
        }
    }

    protected long insert(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql, new String[] { idColumn })) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("INSERT into " + table + " returned no generated id");
                }
                return keys.getLong(1);
            }
        }
    }

    // ---------------------------------------------------------------- column helpers for mapRow

    /** Reads a DATETIME/TIMESTAMP column (stored in UTC) as an Instant, or null. */
    protected static Instant getInstant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /** Reads a nullable INT column (e.g. highest_bidder_id) as an Integer, or null. */
    protected static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** Reads an ENUM / status column into a Java enum, or null. */
    protected static <E extends Enum<E>> E getEnum(ResultSet rs, String column, Class<E> type)
            throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    // ---------------------------------------------------------------- parameter binding

    /**
     * Binds parameters in order. Instants are stored as UTC date-times and enums by name(),
     * so DAOs can pass model values straight through.
     */
    static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;
            if (value == null) {
                ps.setNull(index, Types.NULL);
            } else if (value instanceof Instant instant) {
                ps.setObject(index, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
            } else if (value instanceof Enum<?> e) {
                ps.setString(index, e.name());
            } else {
                ps.setObject(index, value);
            }
        }
    }
}
