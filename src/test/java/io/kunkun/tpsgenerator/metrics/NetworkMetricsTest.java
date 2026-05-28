package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NetworkMetrics.
 */
class NetworkMetricsTest {

    private NetworkMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new NetworkMetrics();
    }

    // -------- recordRequest --------

    @Test
    @DisplayName("recordRequest: increments totalBytesSent")
    void recordRequestIncrementsBytesSent() {
        metrics.recordRequest(null, 512L);
        assertEquals(512L, metrics.getTotalBytesSent());
    }

    @Test
    @DisplayName("recordRequest: accumulates across multiple calls")
    void recordRequestAccumulates() {
        metrics.recordRequest(null, 100L);
        metrics.recordRequest(null, 200L);
        metrics.recordRequest(null, 300L);
        assertEquals(600L, metrics.getTotalBytesSent());
    }

    @Test
    @DisplayName("recordRequest: zero bytes does not change total")
    void recordRequestZeroBytes() {
        metrics.recordRequest(null, 0L);
        assertEquals(0L, metrics.getTotalBytesSent());
    }

    // -------- recordResponse --------

    @Test
    @DisplayName("recordResponse: increments totalBytesReceived")
    void recordResponseIncrementsBytesReceived() {
        HttpResponse<String> response = buildResponse(200, "hello", Collections.emptyMap());
        metrics.recordResponse(response, 100L);
        assertEquals(100L, metrics.getTotalBytesReceived());
    }

    @Test
    @DisplayName("recordResponse: accumulates across multiple calls")
    void recordResponseAccumulates() {
        HttpResponse<String> response = buildResponse(200, "body", Collections.emptyMap());
        metrics.recordResponse(response, 50L);
        metrics.recordResponse(response, 150L);
        assertEquals(200L, metrics.getTotalBytesReceived());
    }

    @Test
    @DisplayName("recordResponse: records content type counter")
    void recordResponseTracksContentType() {
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        HttpResponse<String> response = buildResponse(200, "{}", headers);
        metrics.recordResponse(response, 10L);
        Map<String, Long> contentTypes = metrics.getContentTypeCounts();
        assertEquals(1L, contentTypes.getOrDefault("application/json", 0L));
    }

    // -------- getTotalTraffic / getTotalTrafficMB --------

    @Test
    @DisplayName("getTotalTraffic: sums sent and received bytes")
    void getTotalTrafficSumsBothDirections() {
        metrics.recordRequest(null, 300L);
        HttpResponse<String> response = buildResponse(200, "", Collections.emptyMap());
        metrics.recordResponse(response, 700L);
        assertEquals(1000L, metrics.getTotalTraffic());
    }

    @Test
    @DisplayName("getTotalTrafficMB: converts bytes to megabytes")
    void getTotalTrafficMBConvertsCorrectly() {
        long oneMB = 1024L * 1024L;
        metrics.recordRequest(null, oneMB);
        assertEquals(1.0, metrics.getTotalTrafficMB(), 0.001);
    }

    // -------- estimateResponseSize --------

    @Test
    @DisplayName("estimateResponseSize: includes body bytes")
    void estimateResponseSizeIncludesBody() {
        HttpResponse<String> response = buildResponse(200, "hello", Collections.emptyMap());
        long size = NetworkMetrics.estimateResponseSize(response);
        assertTrue(size >= 5, "Size should be at least 5 bytes for 'hello'");
    }

    @Test
    @DisplayName("estimateResponseSize: returns positive value even with empty body")
    void estimateResponseSizeEmptyBody() {
        HttpResponse<String> response = buildResponse(200, "", Collections.emptyMap());
        long size = NetworkMetrics.estimateResponseSize(response);
        assertTrue(size >= 0);
    }

    // -------- percentile histograms --------

    @Test
    @DisplayName("getRequestSizePercentile: returns 0 when no requests recorded")
    void requestSizePercentileZeroWhenEmpty() {
        // An empty histogram should return 0
        assertEquals(0L, metrics.getRequestSizePercentile(50));
    }

    @Test
    @DisplayName("getResponseSizePercentile: returns 0 when no responses recorded")
    void responseSizePercentileZeroWhenEmpty() {
        assertEquals(0L, metrics.getResponseSizePercentile(50));
    }

    // -------- helper --------

    private HttpResponse<String> buildResponse(int statusCode, String body,
                                                Map<String, List<String>> headerMap) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(headerMap, (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("http://example.com"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
