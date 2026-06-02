package io.kunkun.tpsgenerator.metrics.exporter;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.metrics.TestMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DashboardClient}, including the regression where a null api-key caused
 * HttpRequest.Builder.header("X-API-Key", null) to throw.
 */
class DashboardClientTest {

    private TestConfig config(String apiKey) {
        TestConfig c = new TestConfig();
        c.setName("t");
        c.setTargetServiceUrl("http://target");
        c.setTestDuration(Duration.ofSeconds(1));
        TestConfig.MetricsConfig mc = new TestConfig.MetricsConfig();
        TestConfig.MetricsConfig.DashboardConfig d = new TestConfig.MetricsConfig.DashboardConfig();
        d.setEnabled(true);
        d.setUrl("http://dash");
        d.setApiKey(apiKey);
        mc.setDashboard(d);
        c.setMetrics(mc);
        return c;
    }

    @Test
    @DisplayName("sendTestResult posts to the result endpoint with the configured api key")
    void sendTestResultPostsWithApiKey() {
        CapturingHttpClient stub = new CapturingHttpClient();
        DashboardClient dc = new DashboardClient(config("secret"), "tid", stub);

        dc.sendTestResult(new TestMetrics());

        assertNotNull(stub.lastRequest);
        assertEquals("http://dash/api/tests/result", stub.lastRequest.uri().toString());
        assertEquals(Optional.of("secret"), stub.lastRequest.headers().firstValue("X-API-Key"));
    }

    @Test
    @DisplayName("sendTestResult with a null api key omits the header and does not throw")
    void sendTestResultNullApiKeyNoHeader() {
        CapturingHttpClient stub = new CapturingHttpClient();
        DashboardClient dc = new DashboardClient(config(null), "tid", stub);

        assertDoesNotThrow(() -> dc.sendTestResult(new TestMetrics()));
        assertNotNull(stub.lastRequest);
        assertTrue(stub.lastRequest.headers().firstValue("X-API-Key").isEmpty());
    }

    @Test
    @DisplayName("constructor rejects disabled dashboard config")
    void constructorRejectsDisabled() {
        TestConfig c = config("k");
        c.getMetrics().getDashboard().setEnabled(false);
        assertThrows(IllegalArgumentException.class,
                () -> new DashboardClient(c, "tid", new CapturingHttpClient()));
    }

    /** HttpClient stub that captures the most recent synchronous request and returns 200. */
    private static final class CapturingHttpClient extends HttpClient {
        volatile HttpRequest lastRequest;

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.lastRequest = request;
            return (HttpResponse<T>) ok();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return java.util.concurrent.CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        private HttpResponse<String> ok() {
            return new HttpResponse<>() {
                @Override public int statusCode() { return 200; }
                @Override public HttpRequest request() { return lastRequest; }
                @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Collections.emptyMap(), (a, b) -> true); }
                @Override public String body() { return "ok"; }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return URI.create("http://dash"); }
                @Override public HttpClient.Version version() { return Version.HTTP_1_1; }
            };
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
}
