package edu.usc.team5.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * One shared Gson instance, so every servlet and WebSocket message uses the same JSON format.
 *
 * Times ({@link Instant}) are written as ISO-8601 UTC strings like "2026-11-22T18:30:00Z",
 * which JavaScript reads directly with {@code new Date(str)}. Gson is thread-safe, so sharing
 * one instance across Tomcat's threads is fine.
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
        .disableHtmlEscaping()
        .create();

    private Json() {}

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    public static <T> T fromJson(Reader reader, Class<T> type) {
        return GSON.fromJson(reader, type);
    }

    /** For the rare case that needs Gson directly (e.g. building a JsonObject). */
    public static Gson gson() {
        return GSON;
    }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String text = in.nextString();
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                throw new JsonParseException("Invalid timestamp: " + text, e);
            }
        }
    }
}
