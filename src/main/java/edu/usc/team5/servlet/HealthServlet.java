package edu.usc.team5.servlet;

import edu.usc.team5.util.Db;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.util.Map;

/**
 * GET /api/health: checks that the backend is running and can reach the database.
 * Open it in a browser after setup or a deploy. Returns 200 when the database answers, 503 if not.
 */
@WebServlet("/api/health")
public class HealthServlet extends BaseApiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (Connection conn = Db.getConnection()) {
            boolean ok = conn.isValid(2);
            String database = conn.getMetaData().getDatabaseProductName();
            writeJson(res, ok ? 200 : 503, Map.of("status", ok ? "ok" : "down", "database", database));
        } catch (Exception e) {
            log("Health check could not reach the database", e);
            writeJson(res, 503, Map.of("status", "down", "error", "Database unreachable"));
        }
    }
}
