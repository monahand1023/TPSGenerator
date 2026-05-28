package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RequestTracker.
 */
class RequestTrackerTest {

    private RequestTracker tracker;
    private HttpRequest sampleRequest;

    @BeforeEach
    void setUp() {
        tracker = new RequestTracker();
        sampleRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://example.com/test"))
                .GET()
                .build();
    }

    @Test
    @DisplayName("startTracking: marks request as active")
    void startTrackingMarksActive() {
        tracker.startTracking(1L, sampleRequest);
        assertTrue(tracker.isTracking(1L));
        assertEquals(1, tracker.getActiveCount());
    }

    @Test
    @DisplayName("startTracking: returns RequestInfo with correct request")
    void startTrackingReturnsInfo() {
        RequestTracker.RequestInfo info = tracker.startTracking(42L, sampleRequest);
        assertNotNull(info);
        assertSame(sampleRequest, info.getRequest());
        assertTrue(info.getStartTime() > 0);
    }

    @Test
    @DisplayName("stopTracking: removes request from active set")
    void stopTrackingRemovesRequest() {
        tracker.startTracking(1L, sampleRequest);
        tracker.stopTracking(1L);
        assertFalse(tracker.isTracking(1L));
        assertEquals(0, tracker.getActiveCount());
    }

    @Test
    @DisplayName("stopTracking: returns the RequestInfo that was started")
    void stopTrackingReturnsInfo() {
        tracker.startTracking(7L, sampleRequest);
        RequestTracker.RequestInfo info = tracker.stopTracking(7L);
        assertNotNull(info);
        assertSame(sampleRequest, info.getRequest());
    }

    @Test
    @DisplayName("getActiveCount: reflects multiple in-flight requests")
    void activeCountReflectsInFlightRequests() {
        tracker.startTracking(1L, sampleRequest);
        tracker.startTracking(2L, sampleRequest);
        tracker.startTracking(3L, sampleRequest);
        assertEquals(3, tracker.getActiveCount());
    }

    @Test
    @DisplayName("getActiveCount: decrements when request completes")
    void activeCountDecrementsOnComplete() {
        tracker.startTracking(1L, sampleRequest);
        tracker.startTracking(2L, sampleRequest);
        tracker.stopTracking(1L);
        assertEquals(1, tracker.getActiveCount());
    }

    @Test
    @DisplayName("double-complete same ID: second stopTracking returns null gracefully")
    void doubleCompleteReturnsNull() {
        tracker.startTracking(99L, sampleRequest);
        RequestTracker.RequestInfo first = tracker.stopTracking(99L);
        RequestTracker.RequestInfo second = tracker.stopTracking(99L);
        assertNotNull(first);
        assertNull(second); // ConcurrentHashMap.remove returns null when key absent
    }

    @Test
    @DisplayName("stopTracking: returns null for unknown ID")
    void stopTrackingUnknownIdReturnsNull() {
        assertNull(tracker.stopTracking(12345L));
    }

    @Test
    @DisplayName("clear: removes all active requests")
    void clearRemovesAll() {
        tracker.startTracking(1L, sampleRequest);
        tracker.startTracking(2L, sampleRequest);
        tracker.clear();
        assertEquals(0, tracker.getActiveCount());
    }

    @Test
    @DisplayName("isTracking: returns false when request not started")
    void isTrackingFalseForUnknown() {
        assertFalse(tracker.isTracking(999L));
    }
}
