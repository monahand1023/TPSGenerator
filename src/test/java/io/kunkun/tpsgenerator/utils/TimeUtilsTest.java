package io.kunkun.tpsgenerator.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimeUtils.
 */
class TimeUtilsTest {

    // -------- parseDuration --------

    @Test
    @DisplayName("parseDuration: seconds only")
    void parseDurationSecondsOnly() {
        assertEquals(Duration.ofSeconds(30), TimeUtils.parseDuration("30s"));
    }

    @Test
    @DisplayName("parseDuration: minutes only")
    void parseDurationMinutesOnly() {
        assertEquals(Duration.ofMinutes(5), TimeUtils.parseDuration("5m"));
    }

    @Test
    @DisplayName("parseDuration: hours only")
    void parseDurationHoursOnly() {
        assertEquals(Duration.ofHours(2), TimeUtils.parseDuration("2h"));
    }

    @Test
    @DisplayName("parseDuration: milliseconds only")
    void parseDurationMillisOnly() {
        assertEquals(Duration.ofMillis(500), TimeUtils.parseDuration("500ms"));
    }

    @Test
    @DisplayName("parseDuration: combined hours, minutes, seconds")
    void parseDurationCombined() {
        Duration expected = Duration.ofHours(1).plusMinutes(30).plusSeconds(15);
        assertEquals(expected, TimeUtils.parseDuration("1h30m15s"));
    }

    @Test
    @DisplayName("parseDuration: throws on null input")
    void parseDurationThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtils.parseDuration(null));
    }

    @Test
    @DisplayName("parseDuration: throws on empty string")
    void parseDurationThrowsOnEmpty() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtils.parseDuration("  "));
    }

    // -------- formatDuration --------

    @Test
    @DisplayName("formatDuration: zero seconds")
    void formatDurationZeroSeconds() {
        String result = TimeUtils.formatDuration(Duration.ofSeconds(0));
        assertEquals("0s", result);
    }

    @Test
    @DisplayName("formatDuration: seconds only")
    void formatDurationSeconds() {
        assertEquals("45s", TimeUtils.formatDuration(Duration.ofSeconds(45)));
    }

    @Test
    @DisplayName("formatDuration: minutes and seconds")
    void formatDurationMinutesAndSeconds() {
        String result = TimeUtils.formatDuration(Duration.ofMinutes(2).plusSeconds(30));
        assertTrue(result.contains("2m"));
        assertTrue(result.contains("30s"));
    }

    @Test
    @DisplayName("formatDuration: hours, minutes, seconds")
    void formatDurationHoursMinutesSeconds() {
        String result = TimeUtils.formatDuration(Duration.ofHours(1).plusMinutes(5).plusSeconds(10));
        assertTrue(result.contains("1h"));
        assertTrue(result.contains("5m"));
        assertTrue(result.contains("10s"));
    }

    @Test
    @DisplayName("formatDuration: milliseconds included when under one minute")
    void formatDurationMillis() {
        String result = TimeUtils.formatDuration(Duration.ofMillis(1500));
        assertTrue(result.contains("1s"));
        assertTrue(result.contains("500ms"));
    }

    // -------- formatTimestamp --------

    @Test
    @DisplayName("formatTimestamp: produces non-null, non-empty string")
    void formatTimestampProducesNonEmptyString() {
        String result = TimeUtils.formatTimestamp(System.currentTimeMillis());
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("formatTimestamp: epoch zero produces a timestamp string matching local year")
    void formatTimestampEpochZero() {
        // Epoch 0 in the local timezone may be 1969 or 1970 depending on timezone offset.
        // We verify the format (yyyy-MM-dd HH:mm:ss.SSS) rather than a specific year.
        String result = TimeUtils.formatTimestamp(0L);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
                "Expected timestamp pattern 'yyyy-MM-dd HH:mm:ss.SSS' but got: " + result);
    }

    // -------- getElapsedTimeMs --------

    @Test
    @DisplayName("getElapsedTimeMs: elapsed is non-negative")
    void getElapsedTimeMsIsNonNegative() {
        long start = System.currentTimeMillis();
        long elapsed = TimeUtils.getElapsedTimeMs(start);
        assertTrue(elapsed >= 0);
    }

    // -------- formatElapsedTime --------

    @Test
    @DisplayName("formatElapsedTime: 0 ms formats correctly")
    void formatElapsedTimeZero() {
        String result = TimeUtils.formatElapsedTime(0);
        assertEquals("0s", result);
    }

    @Test
    @DisplayName("formatElapsedTime: large value includes hours")
    void formatElapsedTimeLargeValue() {
        long twoHoursMs = 2 * 3600 * 1000L;
        String result = TimeUtils.formatElapsedTime(twoHoursMs);
        assertTrue(result.contains("2h"), "Expected '2h' in: " + result);
    }

    // -------- hasDurationElapsed --------

    @Test
    @DisplayName("hasDurationElapsed: returns true when duration of 0 has elapsed")
    void hasDurationElapsedWithZeroDuration() {
        long start = System.currentTimeMillis() - 1000;
        assertTrue(TimeUtils.hasDurationElapsed(start, 500));
    }

    @Test
    @DisplayName("hasDurationElapsed: returns false for future duration")
    void hasDurationElapsedReturnsFalseForFuture() {
        long start = System.currentTimeMillis();
        assertFalse(TimeUtils.hasDurationElapsed(start, Long.MAX_VALUE));
    }
}
