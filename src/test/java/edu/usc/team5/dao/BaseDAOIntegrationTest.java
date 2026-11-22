package edu.usc.team5.dao;

import edu.usc.team5.util.Db;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks BaseDAO against a real database. Runs only when RUN_DB_TESTS=true and a database is
 * configured (db.properties or DB_URL) with the schema loaded. Everything it writes is rolled
 * back, so it never leaves rows behind.
 *
 *   RUN_DB_TESTS=true mvn test
 */
class BaseDAOIntegrationTest {

    record Game(int id, String opponent, Instant gameDate) {}

    static class GameDAO extends BaseDAO<Game> {
        GameDAO() { super("Games", "game_id"); }

        @Override
        protected Game mapRow(ResultSet rs) throws SQLException {
            return new Game(rs.getInt("game_id"), rs.getString("opponent"), getInstant(rs, "game_date"));
        }

        long add(Connection c, String opponent, Instant date) throws SQLException {
            return insert(c, "INSERT INTO Games (opponent, game_date, location) VALUES (?, ?, ?)",
                          opponent, date, "LA Memorial Coliseum");
        }

        List<Game> upcoming(Connection c, Instant from) throws SQLException {
            return queryList(c, "SELECT * FROM Games WHERE game_date >= ? ORDER BY game_date", from);
        }
    }

    @BeforeAll
    static void onlyWhenAsked() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_DB_TESTS")),
                   "Set RUN_DB_TESTS=true to run database tests");
    }

    @AfterAll
    static void closePool() {
        Db.shutdown();
    }

    @Test
    void insertFindAndQueryRoundTrip() throws SQLException {
        GameDAO dao = new GameDAO();
        Instant kickoff = Instant.parse("2030-09-06T19:30:00Z");
        try (Connection c = Db.getConnection()) {
            c.setAutoCommit(false);
            try {
                long id = dao.add(c, "UCLA", kickoff);
                assertTrue(id > 0);

                Optional<Game> found = dao.findById(c, id);
                assertTrue(found.isPresent());
                assertEquals("UCLA", found.get().opponent());
                // Times come back exactly as written, in UTC, whatever the server's time zone
                assertEquals(kickoff, found.get().gameDate());

                List<Game> upcoming = dao.upcoming(c, kickoff.minus(1, ChronoUnit.DAYS));
                assertTrue(upcoming.stream().anyMatch(g -> g.id() == id));
                assertEquals(1, dao.queryLong(c, "SELECT COUNT(*) FROM Games WHERE game_id = ?", id));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void inTransactionRollsBackOnError() throws SQLException {
        GameDAO dao = new GameDAO();
        long[] id = new long[1];
        try {
            Db.inTransaction(c -> {
                id[0] = dao.add(c, "Rollback State", Instant.parse("2030-10-01T00:00:00Z"));
                throw new SQLException("simulated failure");
            });
        } catch (SQLException expected) {
            // the insert above must have been undone
        }
        assertTrue(dao.findById(id[0]).isEmpty());
    }
}
