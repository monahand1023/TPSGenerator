package io.kunkun.tpsgenerator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link FlexibleDurationDeserializer}.
 */
class FlexibleDurationDeserializerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Duration.class, new FlexibleDurationDeserializer());
        mapper.registerModule(module);
    }

    private Duration parse(String json) throws Exception {
        return mapper.readValue('"' + json + '"', Duration.class);
    }

    @Test
    @DisplayName("Parses single human-friendly units")
    void parsesSingleUnits() throws Exception {
        assertEquals(Duration.ofMillis(500), parse("500ms"));
        assertEquals(Duration.ofSeconds(30), parse("30s"));
        assertEquals(Duration.ofSeconds(60), parse("60s"));
        assertEquals(Duration.ofMinutes(10), parse("10m"));
        assertEquals(Duration.ofHours(2), parse("2h"));
        assertEquals(Duration.ofDays(1), parse("1d"));
    }

    @Test
    @DisplayName("Parses compound human-friendly durations")
    void parsesCompound() throws Exception {
        assertEquals(Duration.ofMinutes(90), parse("1h30m"));
        assertEquals(Duration.ofSeconds(90), parse("1m30s"));
    }

    @Test
    @DisplayName("Tolerates whitespace and is case-insensitive")
    void tolerantOfWhitespaceAndCase() throws Exception {
        assertEquals(Duration.ofMinutes(5), parse("5 M"));
        assertEquals(Duration.ofSeconds(45), parse("45 S"));
    }

    @Test
    @DisplayName("Still accepts ISO-8601 for backward compatibility")
    void acceptsIso8601() throws Exception {
        assertEquals(Duration.ofMinutes(10), parse("PT10M"));
        assertEquals(Duration.ofSeconds(90), parse("PT1M30S"));
        assertEquals(Duration.ofHours(26), parse("P1DT2H"));
    }

    @Test
    @DisplayName("Empty string deserializes to zero")
    void emptyIsZero() throws Exception {
        assertEquals(Duration.ZERO, parse(""));
    }

    @Test
    @DisplayName("Unrecognised non-numeric text throws a parse error")
    void garbageThrows() {
        assertThrows(Exception.class, () -> parse("not-a-duration"));
    }
}
