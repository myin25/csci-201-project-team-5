package edu.usc.team5.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaseApiServletTest {

    /** A tiny servlet that exercises the parent's helpers. */
    static class EchoServlet extends BaseApiServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws java.io.IOException {
            int id = pathId(req, 0);
            int userId = requireUserId(req);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = readJson(req, Map.class);
            writeJson(res, 201, Map.of("id", id, "userId", userId, "echo", body.get("msg")));
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) {
            throw new IllegalStateException("boom");
        }
    }

    private final StringWriter out = new StringWriter();

    private HttpServletResponse response() throws Exception {
        HttpServletResponse res = mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(out, true));
        return res;
    }

    private HttpServletRequest request(String method, String pathInfo, String body) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getPathInfo()).thenReturn(pathInfo);
        when(req.getRequestURI()).thenReturn("/api/test" + (pathInfo == null ? "" : pathInfo));
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        return req;
    }

    @Test
    void pathPartsSplitsTheUrl() throws Exception {
        EchoServlet servlet = new EchoServlet();
        assertArrayEquals(new String[] { "42", "bids" }, servlet.pathParts(request("GET", "/42/bids", "")));
        assertArrayEquals(new String[0], servlet.pathParts(request("GET", null, "")));
        assertArrayEquals(new String[0], servlet.pathParts(request("GET", "/", "")));
    }

    @Test
    void guestGets401() throws Exception {
        HttpServletResponse res = response();
        new EchoServlet().service(request("POST", "/7", "{\"msg\":\"hi\"}"), res);
        verify(res).setStatus(401);
        assertEquals("{\"error\":\"Please log in first\"}", out.toString());
    }

    @Test
    void nonNumericIdGets404() throws Exception {
        HttpServletResponse res = response();
        new EchoServlet().service(request("POST", "/abc", "{}"), res);
        verify(res).setStatus(404);
    }

    @Test
    void unexpectedErrorsBecome500WithoutDetails() throws Exception {
        HttpServletResponse res = response();
        EchoServlet servlet = new EchoServlet() {
            @Override
            public void log(String msg, Throwable t) { /* keep test output quiet */ }
        };
        servlet.service(request("GET", null, ""), res);
        verify(res).setStatus(500);
        assertEquals("{\"error\":\"Something went wrong\"}", out.toString());
    }
}
