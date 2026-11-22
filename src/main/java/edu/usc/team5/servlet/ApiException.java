package edu.usc.team5.servlet;

/**
 * Throw from any servlet (or a service it calls) to stop and send an error to the browser.
 * {@link BaseApiServlet} turns it into {@code {"error": message}} with the given HTTP status.
 *
 * <pre>
 * throw new ApiException(400, "Minimum bid must be greater than $0.00");
 * throw new ApiException(409, "This auction has ended");
 * </pre>
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
