package edu.usc.team5.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorTest {

    @AfterEach
    void resetClock() {
        Clock.reset();
    }

    @Test
    void uscEmail() {
        assertTrue(Validator.isUscEmail("tommy.trojan@usc.edu"));
        assertTrue(Validator.isUscEmail("TTrojan@USC.EDU"));
        assertFalse(Validator.isUscEmail("tommy@gmail.com"));
        assertFalse(Validator.isUscEmail("tommy@usc.edu.evil.com"));
        assertFalse(Validator.isUscEmail("@usc.edu"));
        assertFalse(Validator.isUscEmail(null));
    }

    @Test
    void username() {
        assertTrue(Validator.isValidUsername("tommy_trojan1"));
        assertFalse(Validator.isValidUsername("ab"));
        assertFalse(Validator.isValidUsername("has space"));
        assertFalse(Validator.isValidUsername("a".repeat(31)));
        assertFalse(Validator.isValidUsername(null));
    }

    @Test
    void password() {
        assertTrue(Validator.isValidPassword("12345678"));
        assertFalse(Validator.isValidPassword("1234567"));
        assertFalse(Validator.isValidPassword("x".repeat(73)));
        assertFalse(Validator.isValidPassword(null));
    }

    @Test
    void price() {
        assertTrue(Validator.isValidPrice(new BigDecimal("0.01")));
        assertTrue(Validator.isValidPrice(new BigDecimal("10000.00")));
        assertTrue(Validator.isValidPrice(new BigDecimal("25.50")));
        assertFalse(Validator.isValidPrice(BigDecimal.ZERO));
        assertFalse(Validator.isValidPrice(new BigDecimal("-5")));
        assertFalse(Validator.isValidPrice(new BigDecimal("10000.01")));
        assertFalse(Validator.isValidPrice(new BigDecimal("1.999")));
        assertFalse(Validator.isValidPrice(null));
    }

    @Test
    void quantityAndSeat() {
        assertTrue(Validator.isValidQuantity(1));
        assertTrue(Validator.isValidQuantity(10));
        assertFalse(Validator.isValidQuantity(0));
        assertFalse(Validator.isValidQuantity(11));
        assertTrue(Validator.isValidSeatField("12A"));
        assertFalse(Validator.isValidSeatField("   "));
        assertFalse(Validator.isValidSeatField("12345678901"));
    }

    @Test
    void endTime() {
        Instant now = Instant.parse("2026-11-22T18:00:00Z");
        Clock.setFixed(now);
        Instant kickoff = now.plus(Duration.ofDays(3));

        assertTrue(Validator.isValidEndTime(now.plus(Duration.ofMinutes(5)), kickoff));
        assertTrue(Validator.isValidEndTime(kickoff, kickoff));
        assertFalse(Validator.isValidEndTime(now.plus(Duration.ofMinutes(4)), kickoff));
        assertFalse(Validator.isValidEndTime(kickoff.plusSeconds(1), kickoff));
        assertFalse(Validator.isValidEndTime(null, kickoff));
    }
}
