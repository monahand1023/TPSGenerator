package io.kunkun.tpsgenerator.request;

import io.kunkun.tpsgenerator.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RequestGenerator template selection and parameter injection.
 */
class RequestGeneratorTest {

    private TestConfig config;

    @BeforeEach
    void setUp() {
        config = new TestConfig();
        config.setName("test");
        config.setTestDuration(Duration.ofMinutes(1));

        TestConfig.ThreadPoolConfig pool = new TestConfig.ThreadPoolConfig();
        pool.setCoreSize(1);
        pool.setMaxSize(2);
        config.setThreadPool(pool);

        TestConfig.CircuitBreakerConfig cb = new TestConfig.CircuitBreakerConfig();
        cb.setEnabled(false);
        config.setCircuitBreaker(cb);

        config.setParameterSources(new HashMap<>());
    }

    private RequestTemplate makeTemplate(String name, String url, int weight) {
        RequestTemplate t = new RequestTemplate();
        t.setName(name);
        t.setMethod("GET");
        t.setUrlTemplate(url);
        t.setWeight(weight);
        return t;
    }

    @Test
    @DisplayName("Single template is always selected")
    void singleTemplateSingleRequest() {
        config.setRequestTemplates(Arrays.asList(
                makeTemplate("only", "http://example.com/only", 1)
        ));
        RequestGenerator gen = new RequestGenerator(config);

        HttpRequest req = gen.generateRequest(1L, 0L);

        assertTrue(req.uri().toString().contains("/only"));
    }

    @Test
    @DisplayName("Default request parameters (requestId, timestamp) are injected")
    void defaultParametersInjected() {
        RequestTemplate t = new RequestTemplate();
        t.setName("param-test");
        t.setMethod("GET");
        t.setUrlTemplate("http://example.com/req/${requestId}");
        t.setWeight(1);
        config.setRequestTemplates(Arrays.asList(t));

        RequestGenerator gen = new RequestGenerator(config);
        HttpRequest req = gen.generateRequest(42L, 0L);

        assertEquals("http://example.com/req/42", req.uri().toString());
    }

    @Test
    @DisplayName("Weighted selection distributes roughly 3:1 across 1000 iterations")
    void weightedSelectionIsApproximatelyCorrect() {
        // Template A has weight 3, template B has weight 1 -> ~75% A, ~25% B
        RequestTemplate a = makeTemplate("a", "http://example.com/a", 3);
        RequestTemplate b = makeTemplate("b", "http://example.com/b", 1);
        config.setRequestTemplates(Arrays.asList(a, b));

        RequestGenerator gen = new RequestGenerator(config);

        int countA = 0;
        int total = 1000;
        for (int i = 0; i < total; i++) {
            HttpRequest req = gen.generateRequest(i, 0L);
            if (req.uri().toString().contains("/a")) {
                countA++;
            }
        }

        double ratioA = (double) countA / total;
        // With 3:1 weighting, expect ~75%. Allow ±10% for randomness.
        assertTrue(ratioA >= 0.65 && ratioA <= 0.85,
                String.format("Expected ~75%% for template A but got %.1f%%", ratioA * 100));
    }

    @Test
    @DisplayName("Multiple templates with equal weight are roughly evenly distributed")
    void equalWeightDistribution() {
        RequestTemplate a = makeTemplate("a", "http://example.com/a", 1);
        RequestTemplate b = makeTemplate("b", "http://example.com/b", 1);
        config.setRequestTemplates(Arrays.asList(a, b));

        RequestGenerator gen = new RequestGenerator(config);

        Map<String, Integer> counts = new HashMap<>();
        counts.put("/a", 0);
        counts.put("/b", 0);

        int total = 1000;
        for (int i = 0; i < total; i++) {
            HttpRequest req = gen.generateRequest(i, 0L);
            String path = req.uri().getPath();
            counts.merge(path, 1, Integer::sum);
        }

        double ratioA = (double) counts.get("/a") / total;
        // Expect ~50% each, allow ±10%
        assertTrue(ratioA >= 0.40 && ratioA <= 0.60,
                String.format("Expected ~50%% for /a but got %.1f%%", ratioA * 100));
    }
}
