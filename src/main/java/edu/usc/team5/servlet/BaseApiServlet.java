package edu.usc.team5.servlet;

import com.google.gson.JsonParseException;
import edu.usc.team5.util.Json;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;

/**
 * Parent of every servlet (LoginServlet, ListingServlet, BidServlet, ...).
 *
 * Handles the parts every endpoint repeats: reading the JSON body, writing a JSON response,
 * sending errors in one consistent shape, and finding the logged-in user. A child servlet
 * only contains its own logic:
 *
 * <pre>
 * &#64;WebServlet("/api/listings/*")
 * public class ListingServlet extends BaseApiServlet {
 *     protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
 *         int sellerId = requireUserId(req);                        // 401 if not logged in
 *         CreateListingRequest body = readJson(req, CreateListingRequest.class);
 *         writeJson(res, 201, listingService.create(sellerId, body));
 *     }
 * }
 * </pre>
 *
 * Error handling: throw {@link ApiException} for expected problems (bad input, not allowed,
 * auction ended). Malformed JSON becomes 400. SQLExceptions and anything unexpected are
 * logged and become 500 "Something went wrong" without leaking details to the browser.
 * Child servlets can wrap a SQLException with {@code throw new ServletException(e)}.
 */
public abstract class BaseApiServlet extends HttpServlet {

    /** Session attribute names. AuthService sets them at login; AuthFilter and servlets read them. */
    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_ROLE = "role";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        res.setCharacterEncoding("UTF-8");
        try {
            super.service(req, res);
        } catch (ApiException e) {
            sendError(res, e.getStatus(), e.getMessage());
        } catch (JsonParseException e) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Request body is not valid JSON");
        } catch (ServletException | RuntimeException e) {
            Throwable cause = (e instanceof ServletException && e.getCause() != null) ? e.getCause() : e;
            if (cause instanceof ApiException api) {
                sendError(res, api.getStatus(), api.getMessage());
                return;
            }
            log("Unhandled error in " + req.getMethod() + " " + req.getRequestURI(), e);
            sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Something went wrong");
        }
    }

    // ---------------------------------------------------------------- JSON in / out

    /** Parses the request body into {@code type}. Sends 400 if the body is missing or not JSON. */
    protected <T> T readJson(HttpServletRequest req, Class<T> type) throws IOException {
        T value = Json.fromJson(req.getReader(), type);
        if (value == null) {
            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Request body is required");
        }
        return value;
    }

    /** Sends {@code body} as JSON with status 200. */
    protected void writeJson(HttpServletResponse res, Object body) throws IOException {
        writeJson(res, HttpServletResponse.SC_OK, body);
    }

    /** Sends {@code body} as JSON with the given status (e.g. 201 after creating something). */
    protected void writeJson(HttpServletResponse res, int status, Object body) throws IOException {
        if (res.isCommitted()) {
            return;
        }
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write(Json.toJson(body));
    }

    /** Sends {@code {"error": message}} with the given status. The frontend shows the message. */
    protected void sendError(HttpServletResponse res, int status, String message) throws IOException {
        writeJson(res, status, Map.of("error", message));
    }

    // ---------------------------------------------------------------- who is logged in

    /** The logged-in user's id, or null for a guest. */
    protected Integer currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SESSION_USER_ID);
    }

    /** The logged-in user's role ("USER" or "ADMIN"), or null for a guest. */
    protected String currentRole(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (String) session.getAttribute(SESSION_ROLE);
    }

    /** The logged-in user's id. Stops with 401 if this is a guest. */
    protected int requireUserId(HttpServletRequest req) {
        Integer userId = currentUserId(req);
        if (userId == null) {
            throw new ApiException(HttpServletResponse.SC_UNAUTHORIZED, "Please log in first");
        }
        return userId;
    }

    // TODO(model team): once model.User exists, add
    //   protected User currentUser(HttpServletRequest req)
    // returning the User that AuthFilter attaches as a request attribute (GuestUser for guests).

    // ---------------------------------------------------------------- URL path helpers

    /**
     * The parts of the URL after the servlet's mapping. For a servlet mapped to
     * "/api/listings/*", the URL /api/listings/42/bids gives ["42", "bids"]; /api/listings gives [].
     */
    protected String[] pathParts(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) {
            return new String[0];
        }
        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    /** The numeric id at {@code pathParts(req)[index]}. Stops with 404 if it's missing or not a number. */
    protected int pathId(HttpServletRequest req, int index) {
        String[] parts = pathParts(req);
        try {
            return Integer.parseInt(parts[index]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            throw new ApiException(HttpServletResponse.SC_NOT_FOUND, "Not found");
        }
    }

    /** Wraps a SQLException so it can be thrown from doGet/doPost; becomes a logged 500. */
    protected static ServletException dbError(SQLException e) {
        return new ServletException("Database error", e);
    }
}
