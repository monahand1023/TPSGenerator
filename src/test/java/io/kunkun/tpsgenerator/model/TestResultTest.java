package io.kunkun.tpsgenerator.model;

import io.kunkun.tpsgenerator.metrics.TestMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TestResult.
 */
class TestResultTest {

    private TestResult result;
    private TestMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new TestMetrics();
        metrics.setStartTime(1_000_000L);
        metrics.setEndTime(1_060_000L);
        metrics.setDuration(60_000L);
        metrics.setAverageTps(10.0);

        result = new TestResult("my-test", 1_000_000L, 1_060_000L, metrics);
    }

    @Test
    @DisplayName("getTestName returns the value set in constructor")
    void testNameReturned() {
        assertEquals("my-test", result.getTestName());
    }

    @Test
    @DisplayName("getStartTimeMs returns start time")
    void startTimeMsReturned() {
        assertEquals(1_000_000L, result.getStartTimeMs());
    }

    @Test
    @DisplayName("getEndTimeMs returns end time")
    void endTimeMsReturned() {
        assertEquals(1_060_000L, result.getEndTimeMs());
    }

    @Test
    @DisplayName("getDurationMs computes endTime - startTime")
    void durationMsComputed() {
        assertEquals(60_000L, result.getDurationMs());
    }

    @Test
    @DisplayName("getDurationSeconds converts milliseconds to seconds")
    void durationSecondsComputed() {
        assertEquals(60.0, result.getDurationSeconds(), 0.001);
    }

    @Test
    @DisplayName("getFormattedStartTime returns non-empty string")
    void formattedStartTimeNonEmpty() {
        String fmt = result.getFormattedStartTime();
        assertNotNull(fmt);
        assertFalse(fmt.isEmpty());
    }

    @Test
    @DisplayName("getFormattedEndTime returns non-empty string")
    void formattedEndTimeNonEmpty() {
        String fmt = result.getFormattedEndTime();
        assertNotNull(fmt);
        assertFalse(fmt.isEmpty());
    }

    @Test
    @DisplayName("getSummary includes test name and duration")
    void summaryIncludesTestNameAndDuration() {
        metrics.updateHistogramSnapshots();
        String summary = result.getSummary();
        assertTrue(summary.contains("my-test"), "Summary should contain test name");
        assertTrue(summary.contains("60.00"), "Summary should contain duration");
    }

    @Test
    @DisplayName("no-args constructor then setters works correctly")
    void noArgsConstructorAndSetters() {
        TestResult r = new TestResult();
        r.setTestName("test2");
        r.setStartTimeMs(0L);
        r.setEndTimeMs(5000L);
        r.setMetrics(metrics);

        assertEquals("test2", r.getTestName());
        assertEquals(5000L, r.getDurationMs());
    }

    @Test
    @DisplayName("all-args constructor sets all fields")
    void allArgsConstructorSetsAllFields() {
        assertEquals("my-test", result.getTestName());
        assertSame(metrics, result.getMetrics());
    }
}
