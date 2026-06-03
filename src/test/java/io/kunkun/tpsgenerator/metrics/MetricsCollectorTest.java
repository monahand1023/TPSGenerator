package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.config.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsCollector.
 */
class MetricsCollectorTest {

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector(buildConfig(false));
    }

    @AfterEach
    void tearDown() {
        collector.stop();
    }

    // -------- start / stop --------

    @Test
    @DisplayName("start: sets startTime to a positive value")
    void startSetsStartTime() {
        collector.start();
        assertTrue(collector.getStartTime() > 0);
    }

    @Test
    @DisplayName("start: is idempotent (calling twice does not throw)")
    void startIsIdempotent() {
        assertDoesNotThrow(() -> {
            collector.start();
            collector.start();
        });
    }

    @Test
    @DisplayName("stop: is idempotent (calling twice does not throw)")
    void stopIsIdempotent() {
        collector.start();
        assertDoesNotThrow(() -> {
            collector.stop();
            collector.stop();
        });
    }

    @Test
    @DisplayName("close: delegates to stop without throwing")
    void closeDelegatesToStop() {
        collector.start();
        assertDoesNotThrow(collector::close);
    }

    // -------- sub-collectors exposed --------

    @Test
    @DisplayName("getTestMetrics returns non-null")
    void getTestMetricsNonNull() {
        assertNotNull(collector.getTestMetrics());
    }

    @Test
    @DisplayName("getNetworkMetrics returns non-null")
    void getNetworkMetricsNonNull() {
        assertNotNull(collector.getNetworkMetrics());
    }

    @Test
    @DisplayName("getErrorAnalyzer returns non-null")
    void getErrorAnalyzerNonNull() {
        assertNotNull(collector.getErrorAnalyzer());
    }

    @Test
    @DisplayName("getRequestTracker returns non-null")
    void getRequestTrackerNonNull() {
        assertNotNull(collector.getRequestTracker());
    }

    @Test
    @DisplayName("resourceMonitor is null when resource monitoring is disabled")
    void resourceMonitorNullWhenDisabled() {
        assertNull(collector.getResourceMonitor());
    }

    @Test
    @DisplayName("resourceMonitor is non-null when resource monitoring is enabled")
    void resourceMonitorNonNullWhenEnabled() {
        MetricsCollector c = new MetricsCollector(buildConfig(true));
        assertNotNull(c.getResourceMonitor());
        c.stop();
    }

    // -------- recordRequestStart --------

    @Test
    @DisplayName("recordRequestStart: increments totalRequests")
    void recordRequestStartIncrementsTotalRequests() {
        collector.start();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://example.com"))
                .GET()
                .build();
        collector.recordRequestStart(1L, req);
        assertEquals(1L, collector.getTestMetrics().getTotalRequests());
    }

    @Test
    @DisplayName("recordRequestStart: marks request as tracked")
    void recordRequestStartTracksRequest() {
        collector.start();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://example.com"))
                .GET()
                .build();
        collector.recordRequestStart(42L, req);
        assertTrue(collector.getRequestTracker().isTracking(42L));
    }

    // -------- recordResponse (success) --------

    @Test
    @DisplayName("recordResponse with 200: increments successCount")
    void recordResponseSuccessIncrements() {
        collector.start();
        HttpRequest req = buildHttpRequest();
        collector.recordRequestStart(1L, req);
        HttpResponse<String> response = buildResponse(200, "OK");
        collector.recordResponse(1L, response, 100L);
        assertEquals(1L, collector.getTestMetrics().getSuccessCount());
        assertEquals(0L, collector.getTestMetrics().getFailureCount());
    }

    @Test
    @DisplayName("recordResponse: removes request from tracker")
    void recordResponseRemovesFromTracker() {
        collector.start();
        HttpRequest req = buildHttpRequest();
        collector.recordRequestStart(5L, req);
        collector.recordResponse(5L, buildResponse(200, ""), 50L);
        assertFalse(collector.getRequestTracker().isTracking(5L));
    }

    // -------- recordResponse (failure) --------

    @Test
    @DisplayName("recordResponse with 500: increments failureCount")
    void recordResponse500IncrementFailure() {
        collector.start();
        collector.recordRequestStart(2L, buildHttpRequest());
        collector.recordResponse(2L, buildResponse(500, "Error"), 150L);
        assertEquals(1L, collector.getTestMetrics().getFailureCount());
        assertEquals(0L, collector.getTestMetrics().getSuccessCount());
    }

    // -------- recordError --------

    @Test
    @DisplayName("recordError: increments failureCount")
    void recordErrorIncrementsFailure() {
        collector.start();
        collector.recordRequestStart(3L, buildHttpRequest());
        collector.recordError(3L, new RuntimeException("connection refused"));
        assertEquals(1L, collector.getTestMetrics().getFailureCount());
    }

    @Test
    @DisplayName("recordError: records exception in ErrorAnalyzer")
    void recordErrorDelegatesToErrorAnalyzer() {
        collector.start();
        collector.recordRequestStart(4L, buildHttpRequest());
        collector.recordError(4L, new RuntimeException("timeout"));
        assertEquals(1L, collector.getErrorAnalyzer().getTotalExceptionCount());
    }

    // -------- recordTimeout --------

    @Test
    @DisplayName("recordTimeout: increments timeoutCount and failureCount")
    void recordTimeoutIncrementsBoth() {
        collector.start();
        collector.recordRequestStart(6L, buildHttpRequest());
        collector.recordTimeout(6L, 30_000L);
        assertEquals(1L, collector.getTestMetrics().getTimeoutCount());
        assertEquals(1L, collector.getTestMetrics().getFailureCount());
    }

    // -------- recordSkippedRequest --------

    @Test
    @DisplayName("recordSkippedRequest: increments skippedCount")
    void recordSkippedRequestIncrementsSkipped() {
        collector.start();
        collector.recordSkippedRequest(7L);
        assertEquals(1L, collector.getTestMetrics().getSkippedCount());
    }

    // -------- non-HTTP (WebSocket) recording --------

    @Test
    @DisplayName("recordRequestStart(id): increments total without counting network bytes")
    void recordRequestStartNoHttpIncrementsTotalOnly() {
        collector.start();
        collector.recordRequestStart(1L);
        assertEquals(1L, collector.getTestMetrics().getTotalRequests());
        assertEquals(0L, collector.getNetworkMetrics().getTotalBytesSent());
    }

    @Test
    @DisplayName("recordOutcome: increments success/failure")
    void recordOutcomeCounts() {
        collector.start();
        collector.recordOutcome(true);
        collector.recordOutcome(false);
        assertEquals(1L, collector.getTestMetrics().getSuccessCount());
        assertEquals(1L, collector.getTestMetrics().getFailureCount());
    }

    @Test
    @DisplayName("recordEndToEndLatency: feeds the response-time histogram (ns -> ms)")
    void recordEndToEndLatencyFeedsHistogram() {
        collector.start();
        collector.recordEndToEndLatency(50_000_000L, 0L); // 50 ms, no CO correction
        collector.getTestMetrics().updateHistogramSnapshots();
        assertTrue(collector.getTestMetrics().getResponseTimePercentile(50) >= 49);
    }

    // -------- getCurrentTps --------

    @Test
    @DisplayName("getCurrentTps: returns non-negative value")
    void getCurrentTpsNonNegative() {
        collector.start();
        assertTrue(collector.getCurrentTps() >= 0.0);
    }

    // -------- helpers --------

    private TestConfig buildConfig(boolean resourceMonitoringEnabled) {
        TestConfig config = new TestConfig();
        TestConfig.MetricsConfig metricsConfig = new TestConfig.MetricsConfig();
        TestConfig.MetricsConfig.ResourceMonitoringConfig rmConfig =
                new TestConfig.MetricsConfig.ResourceMonitoringConfig();
        rmConfig.setEnabled(resourceMonitoringEnabled);
        rmConfig.setSampleInterval(Duration.ofSeconds(1));
        metricsConfig.setResourceMonitoring(rmConfig);
        config.setMetrics(metricsConfig);
        return config;
    }

    private HttpRequest buildHttpRequest() {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://example.com/test"))
                .GET()
                .build();
    }

    private HttpResponse<String> buildResponse(int statusCode, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() {
                return HttpHeaders.of(Collections.emptyMap(), (a, b) -> true);
            }
            @Override public String body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("http://example.com"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
