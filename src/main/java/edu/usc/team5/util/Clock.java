package edu.usc.team5.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The app's single source of "now". Always call {@code Clock.now()} instead of
 * {@code Instant.now()} or the database's own clock, so tests can freeze or move time:
 *
 * <pre>
 * Clock.setFixed(Instant.parse("2026-11-22T18:00:00Z"));
 * Clock.advance(Duration.ofMinutes(6));   // jump past an auction's end time
 * Clock.reset();                          // back to the real clock (do this in @AfterEach)
 * </pre>
 *
 * Passing {@code Clock.now()} into SQL as a parameter (instead of writing UTC_TIMESTAMP() or
 * now() in the query) also keeps queries working on both MySQL and Postgres.
 */
public final class Clock {

    private static volatile java.time.Clock source = java.time.Clock.systemUTC();

    private Clock() {}

    public static Instant now() {
        return source.instant();
    }

    /** Tests only: freeze time at {@code instant}. */
    public static void setFixed(Instant instant) {
        source = java.time.Clock.fixed(instant, ZoneOffset.UTC);
    }

    /** Tests only: move a frozen (or real) clock forward. */
    public static void advance(Duration amount) {
        source = java.time.Clock.offset(source, amount);
    }

    /** Tests only: go back to the real system clock. */
    public static void reset() {
        source = java.time.Clock.systemUTC();
    }
}
