package edu.usc.team5.util;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Input rules shared by every servlet. The frontend checks the same rules for fast feedback,
 * but the server is the authority: always validate here before writing anything.
 * Every method returns false for null input instead of throwing.
 */
public final class Validator {

    private static final Pattern USC_EMAIL =
        Pattern.compile("^[A-Za-z0-9._%+-]+@usc\\.edu$", Pattern.CASE_INSENSITIVE);
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,30}$");

    public static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only looks at the first 72 bytes, so longer passwords would be silently cut. */
    public static final int MAX_PASSWORD_LENGTH = 72;
    public static final BigDecimal MAX_PRICE = new BigDecimal("10000.00");
    public static final Duration MIN_AUCTION_LENGTH = Duration.ofMinutes(5);

    private Validator() {}

    /** Must end in @usc.edu and fit the 100-character column. */
    public static boolean isUscEmail(String email) {
        return email != null && email.length() <= 100 && USC_EMAIL.matcher(email).matches();
    }

    /** 3–30 letters, digits, or underscores. */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    /** At least 8 characters (and at most 72, BCrypt's limit). */
    public static boolean isValidPassword(String password) {
        return password != null
            && password.length() >= MIN_PASSWORD_LENGTH
            && password.length() <= MAX_PASSWORD_LENGTH;
    }

    /** First or last name: 1–50 characters after trimming. */
    public static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.trim().length() <= 50;
    }

    /** Greater than $0.00, at most $10,000.00, and no more than two decimal places. */
    public static boolean isValidPrice(BigDecimal price) {
        return price != null
            && price.signum() > 0
            && price.compareTo(MAX_PRICE) <= 0
            && price.stripTrailingZeros().scale() <= 2;
    }

    /** Number of tickets in one listing: 1–10. */
    public static boolean isValidQuantity(int quantity) {
        return quantity >= 1 && quantity <= 10;
    }

    /** Section or row: required, up to 10 characters. */
    public static boolean isValidSeatField(String value) {
        return value != null && !value.isBlank() && value.trim().length() <= 10;
    }

    /**
     * Auction end time: at least 5 minutes from now and no later than the game's kickoff.
     *
     * @param end      the auction end time the seller picked
     * @param gameDate kickoff time of the chosen game (Games.game_date)
     */
    public static boolean isValidEndTime(Instant end, Instant gameDate) {
        if (end == null || gameDate == null) {
            return false;
        }
        Instant earliest = Clock.now().plus(MIN_AUCTION_LENGTH);
        return !end.isBefore(earliest) && !end.isAfter(gameDate);
    }
}
