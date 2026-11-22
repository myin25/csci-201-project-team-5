package edu.usc.team5.util;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonAndClockTest {

    static class Sample {
        String name;
        BigDecimal price;
        Instant endTime;
    }

    @AfterEach
    void resetClock() {
        Clock.reset();
    }

    @Test
    void instantsRoundTripAsIsoStrings() {
        Sample s = new Sample();
        s.name = "Section 112";
        s.price = new BigDecimal("45.00");
        s.endTime = Instant.parse("2026-11-22T18:30:00Z");

        String json = Json.toJson(s);
        assertEquals("{\"name\":\"Section 112\",\"price\":45.00,\"endTime\":\"2026-11-22T18:30:00Z\"}", json);

        Sample back = Json.fromJson(json, Sample.class);
        assertEquals(s.endTime, back.endTime);
        assertEquals(s.price, back.price);
    }

    @Test
    void badTimestampIsAParseError() {
        assertThrows(JsonParseException.class,
            () -> Json.fromJson("{\"endTime\":\"tomorrow\"}", Sample.class));
    }

    @Test
    void clockCanBeFrozenAndAdvanced() {
        Instant start = Instant.parse("2026-11-22T18:00:00Z");
        Clock.setFixed(start);
        assertEquals(start, Clock.now());
        Clock.advance(Duration.ofMinutes(6));
        assertEquals(start.plus(Duration.ofMinutes(6)), Clock.now());
    }
}
