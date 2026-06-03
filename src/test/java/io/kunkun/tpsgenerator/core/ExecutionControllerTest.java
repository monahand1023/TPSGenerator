package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.metrics.LatencyStats;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.model.TestResult;
import io.kunkun.tpsgenerator.request.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle tests for {@link ExecutionController} using a stub {@link HttpClient}, so no real
 * network is needed. Covers: a full run produces a result with recorded traffic, the latency
 * snapshot is non-destructive, completion is signalled, and close() is safe.
 */
class ExecutionControllerTest {

    @Test
    @DisplayName("execute() runs the full loop and produces a result with recorded traffic")
    void executeProducesResult() throws Exception {
        TestConfig config = config(Duration.ofMillis(300), 100);
        MetricsCollector metrics = new MetricsCollector(config);
        StubHttpClient client = StubHttpClient.returning(response(200, "OK"));

        try (ExecutionController controller = new ExecutionController(config, metrics, client)) {
            TestResult result = controller.execute();

            assertNotNull(result);
            assertNotNull(result.getMetrics());
            assertTrue(metrics.getTestMetrics().getTotalRequests() > 0, "should have issued requests");
            assertTrue(metrics.getTestMetrics().getSuccessCount() > 0, "stub returns 200 → successes");

            // completion latch is signalled
            assertTrue(controller.waitForCompletion(1, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("getLatencyPercentiles() is non-destructive and callable repeatedly")
    void latencyPercentilesAreNonDestructive() throws Exception {
        TestConfig config = config(Duration.ofMillis(300), 100);
        MetricsCollector metrics = new MetricsCollector(config);
        StubHttpClient client = StubHttpClient.returning(response(200, "OK"));

        try (ExecutionController controller = new ExecutionController(config, metrics, client)) {
            controller.execute();

            LatencyStats first = controller.getLatencyPercentiles();
            LatencyStats second = controller.getLatencyPercentiles();

            assertNotNull(first);
            assertTrue(first.getP95Ms() >= 0.0);
            assertEquals(first.getP95Ms(), second.getP95Ms(), 1e-9, "repeated reads must agree");
            assertEquals(first.getMaxMs(), second.getMaxMs(), 1e-9);
        }
    }

    @Test
    @DisplayName("execute() with multiple submission threads still completes and records traffic")
    void executeWithMultipleSubmitters() throws Exception {
        TestConfig config = config(Duration.ofMillis(300), 200);
        config.setSubmissionThreads(4);
        MetricsCollector metrics = new MetricsCollector(config);
        StubHttpClient client = StubHttpClient.returning(response(200, "OK"));

        try (ExecutionController controller = new ExecutionController(config, metrics, client)) {
            controller.execute();
            assertTrue(metrics.getTestMetrics().getTotalRequests() > 0);
            assertTrue(metrics.getTestMetrics().getSuccessCount() > 0);
        }
    }

    @Test
    @DisplayName("response validation wired from config: failing validation counts 200s as failures")
    void responseValidationWiredFromConfig() throws Exception {
        TestConfig config = config(Duration.ofMillis(250), 100);
        TestConfig.ResponseValidationConfig rv = new TestConfig.ResponseValidationConfig();
        rv.setEnabled(true);
        rv.setBodyContains("WILL-NOT-MATCH");
        config.setResponseValidation(rv);

        MetricsCollector metrics = new MetricsCollector(config);
        StubHttpClient client = StubHttpClient.returning(response(200, "OK"));

        try (ExecutionController controller = new ExecutionController(config, metrics, client)) {
            controller.execute();
            assertTrue(metrics.getTestMetrics().getTotalRequests() > 0);
            assertEquals(0L, metrics.getTestMetrics().getSuccessCount(),
                    "every 200 fails the body-contains rule, so none count as success");
            assertTrue(metrics.getTestMetrics().getFailureCount() > 0);
        }
    }

    @Test
    @DisplayName("runs without a circuitBreaker config block (optional)")
    void executeWithoutCircuitBreakerConfig() throws Exception {
        TestConfig config = config(Duration.ofMillis(150), 50);
        config.setCircuitBreaker(null); // omit the optional block

        MetricsCollector metrics = new MetricsCollector(config);
        StubHttpClient client = StubHttpClient.returning(response(200, "OK"));

        try (ExecutionController controller = new ExecutionController(config, metrics, client)) {
            assertDoesNotThrow(controller::execute);
            assertTrue(metrics.getTestMetrics().getTotalRequests() > 0);
        }
    }

    @Test
    @DisplayName("ramp-from-zero TPS does not throw (RateLimiter rejects rate 0)")
    void rampFromZeroTpsDoesNotThrow() throws Exception {
        TestConfig config = config(Duration.ofMillis(200), 50);
        TestConfig.TrafficConfig t = new TestConfig.TrafficConfig();
        t.setType("rampUp");
        t.setStartTps(0);          // ramp from idle — initial rate 0 would crash RateLimiter.create
        t.setTargetTps(50);
        t.setRampDuration(Duration.ofMillis(100));
        config.setTrafficPattern(t);

        MetricsCollector metrics = new MetricsCollector(config);
        StubHttpClient client = StubHttpClient.returning(response(200, "OK"));

        try (ExecutionController controller = new ExecutionController(config, metrics, client)) {
            assertDoesNotThrow(controller::execute);
        }
    }

    @Test
    @DisplayName("scenario extracts a value from one response and uses it in the next step")
    void scenarioChainsExtractedValue() throws Exception {
        TestConfig config = config(Duration.ofMillis(300), 5);
        config.setRequestTemplates(null); // scenario-only

        TestConfig.ScenarioStep login = new TestConfig.ScenarioStep();
        login.setName("login");
        RequestTemplate loginReq = new RequestTemplate();
        loginReq.setMethod("GET");
        loginReq.setUrlTemplate("http://example.com/login");
        login.setRequest(loginReq);
        TestConfig.ExtractRule rule = new TestConfig.ExtractRule();
        rule.setName("token");
        rule.setFrom("body");
        rule.setExpr("\"token\":\"([^\"]+)\"");
        login.setExtract(List.of(rule));

        TestConfig.ScenarioStep fetch = new TestConfig.ScenarioStep();
        fetch.setName("fetch");
        RequestTemplate fetchReq = new RequestTemplate();
        fetchReq.setMethod("GET");
        fetchReq.setUrlTemplate("http://example.com/data?t=${token}");
        fetch.setRequest(fetchReq);

        config.setScenario(List.of(login, fetch));

        MetricsCollector metrics = new MetricsCollector(config);
        RecordingHttpClient client = new RecordingHttpClient("{\"token\":\"abc123\"}");

        try (ExecutionController controller = new ExecutionController(config, metrics, client)) {
            controller.execute();
        }

        assertTrue(client.uris.stream().anyMatch(u -> u.contains("/data?t=abc123")),
                "second step should use the token extracted from step one; saw " + client.uris);
    }

    @Test
    @DisplayName("websocket protocol wires through and records exchange outcomes")
    void webSocketModeRecordsExchanges() throws Exception {
        TestConfig config = config(Duration.ofMillis(250), 20);
        config.setProtocol("websocket");
        config.setTargetServiceUrl("http://localhost:1"); // required for websocket mode
        config.setRequestTemplates(null);

        MetricsCollector metrics = new MetricsCollector(config);
        // The stub client doesn't support WebSocket, so every exchange fails — which exercises the
        // websocket branch + outcome recording without needing a WS server, and must not crash.
        StubHttpClient client = StubHttpClient.returning(response(200, "OK"));

        try (ExecutionController controller = new ExecutionController(config, metrics, client)) {
            controller.execute();
            assertTrue(metrics.getTestMetrics().getTotalRequests() > 0);
            assertTrue(metrics.getTestMetrics().getFailureCount() > 0);
        }
    }

    @Test
    @DisplayName("close() after a completed run does not throw")
    void closeIsSafe() throws Exception {
        TestConfig config = config(Duration.ofMillis(150), 50);
        MetricsCollector metrics = new MetricsCollector(config);
        StubHttpClient client = StubHttpClient.returning(response(200, "OK"));

        ExecutionController controller = new ExecutionController(config, metrics, client);
        controller.execute();
        assertDoesNotThrow(controller::close);
    }

    // -------- helpers --------

    private TestConfig config(Duration duration, double targetTps) {
        TestConfig config = new TestConfig();
        config.setName("lifecycle-test");
        config.setTestDuration(duration);

        TestConfig.TrafficConfig traffic = new TestConfig.TrafficConfig();
        traffic.setType("stable");
        traffic.setTargetTps(targetTps);
        config.setTrafficPattern(traffic);

        RequestTemplate rt = new RequestTemplate();
        rt.setMethod("GET");
        rt.setUrlTemplate("http://example.com/api");
        config.setRequestTemplates(List.of(rt));
        config.setParameterSources(Collections.emptyMap());

        TestConfig.MetricsConfig metricsConfig = new TestConfig.MetricsConfig();
        TestConfig.MetricsConfig.ResourceMonitoringConfig rm =
                new TestConfig.MetricsConfig.ResourceMonitoringConfig();
        rm.setEnabled(false);
        rm.setSampleInterval(Duration.ofSeconds(5));
        metricsConfig.setResourceMonitoring(rm);
        config.setMetrics(metricsConfig);

        TestConfig.CircuitBreakerConfig cb = new TestConfig.CircuitBreakerConfig();
        cb.setEnabled(false);
        config.setCircuitBreaker(cb);

        return config;
    }

    private HttpResponse<String> response(int statusCode, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Collections.emptyMap(), (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("http://example.com"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    /** HttpClient stub that records every request URI and returns 200 with a fixed body. */
    private static final class RecordingHttpClient extends HttpClient {
        final CopyOnWriteArrayList<String> uris = new CopyOnWriteArrayList<>();
        private final String body;

        RecordingHttpClient(String body) {
            this.body = body;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            uris.add(request.uri().toString());
            HttpResponse<String> resp = new HttpResponse<>() {
                @Override public int statusCode() { return 200; }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Collections.emptyMap(), (a, b) -> true); }
                @Override public String body() { return body; }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public HttpClient.Version version() { return Version.HTTP_1_1; }
            };
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) CompletableFuture.completedFuture(resp);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) {
            return sendAsync(request, h);
        }

        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> h) {
            throw new UnsupportedOperationException("Use sendAsync");
        }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return null; }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return null; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
    }

    /** Concrete HttpClient stub (Mockito cannot mock the sealed JDK HttpClient on recent JDKs). */
    private static class StubHttpClient extends HttpClient {
        @SuppressWarnings("rawtypes")
        private final Supplier<CompletableFuture> futureSupplier;

        @SuppressWarnings("rawtypes")
        private StubHttpClient(Supplier<CompletableFuture> futureSupplier) {
            this.futureSupplier = futureSupplier;
        }

        static StubHttpClient returning(HttpResponse<String> response) {
            return new StubHttpClient(() -> CompletableFuture.completedFuture(response));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture) futureSupplier.get();
        }

        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public HttpClient.Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return null; }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return null; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> h) {
            throw new UnsupportedOperationException("Use sendAsync");
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) {
            return sendAsync(request, h);
        }
    }
}
