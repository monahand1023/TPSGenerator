package io.kunkun.tpsgenerator.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kunkun.tpsgenerator.metrics.exporter.JsonExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonExporter.
 */
class JsonExporterTest {

    @TempDir
    Path tempDir;

    private JsonExporter exporter;
    private TestMetrics metrics;
    private LatencyStats latencyStats;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        exporter = new JsonExporter();
        metrics = buildMetrics();
        latencyStats = new LatencyStats(10.0, 20.0, 30.0, 50.0, 12.5);
    }

    @Test
    @DisplayName("exportResults: produces non-empty file")
    void exportResultsProducesNonEmptyFile() throws IOException {
        File output = tempDir.resolve("results.json").toFile();
        exporter.exportResults("my-test", metrics, latencyStats, output);
        assertTrue(output.exists());
        assertTrue(output.length() > 0);
    }

    @Test
    @DisplayName("exportResults: output is valid parseable JSON")
    void exportResultsProducesValidJson() throws IOException {
        File output = tempDir.resolve("valid.json").toFile();
        exporter.exportResults("my-test", metrics, latencyStats, output);
        String content = Files.readString(output.toPath());
        assertDoesNotThrow(() -> mapper.readTree(content), "Output should be valid JSON");
    }

    @Test
    @DisplayName("exportResults: JSON contains testName field")
    void exportResultsContainsTestName() throws IOException {
        File output = tempDir.resolve("name.json").toFile();
        exporter.exportResults("load-test-1", metrics, latencyStats, output);
        JsonNode root = mapper.readTree(output);
        assertEquals("load-test-1", root.path("testName").asText());
    }

    @Test
    @DisplayName("exportResults: JSON contains totalRequests")
    void exportResultsContainsTotalRequests() throws IOException {
        File output = tempDir.resolve("totals.json").toFile();
        exporter.exportResults("test", metrics, latencyStats, output);
        JsonNode root = mapper.readTree(output);
        assertEquals(1L, root.path("totalRequests").asLong());
    }

    @Test
    @DisplayName("exportResults: JSON contains latency sub-object with expected keys")
    void exportResultsContainsLatencyObject() throws IOException {
        File output = tempDir.resolve("latency.json").toFile();
        exporter.exportResults("test", metrics, latencyStats, output);
        JsonNode root = mapper.readTree(output);
        JsonNode latency = root.path("latency");
        assertFalse(latency.isMissingNode(), "latency key should exist");
        assertTrue(latency.has("p50Ms"), "latency should have p50Ms");
        assertTrue(latency.has("p95Ms"), "latency should have p95Ms");
        assertTrue(latency.has("p99Ms"), "latency should have p99Ms");
        assertTrue(latency.has("maxMs"), "latency should have maxMs");
        assertTrue(latency.has("meanMs"), "latency should have meanMs");
    }

    @Test
    @DisplayName("exportResults: latency includes a decodable encoded histogram (for merge)")
    void exportResultsIncludesEncodedHistogram() throws IOException {
        File output = tempDir.resolve("histogram.json").toFile();
        exporter.exportResults("test", metrics, latencyStats, output);
        JsonNode latency = mapper.readTree(output).path("latency");

        assertTrue(latency.has("histogram"), "latency should include the encoded histogram");
        // Must round-trip through the codec the distributed `merge` feature relies on.
        assertDoesNotThrow(() -> HistogramCodec.decode(latency.path("histogram").asText()));
    }

    @Test
    @DisplayName("exportResults: latency values match LatencyStats input")
    void exportResultsLatencyValuesCorrect() throws IOException {
        File output = tempDir.resolve("latency_vals.json").toFile();
        exporter.exportResults("test", metrics, latencyStats, output);
        JsonNode latency = mapper.readTree(output).path("latency");
        assertEquals(10.0, latency.path("p50Ms").asDouble(), 0.001);
        assertEquals(20.0, latency.path("p95Ms").asDouble(), 0.001);
        assertEquals(30.0, latency.path("p99Ms").asDouble(), 0.001);
        assertEquals(50.0, latency.path("maxMs").asDouble(), 0.001);
        assertEquals(12.5, latency.path("meanMs").asDouble(), 0.001);
    }

    @Test
    @DisplayName("exportResults: throws when testName is null")
    void throwsWhenTestNameNull() {
        File output = tempDir.resolve("err.json").toFile();
        assertThrows(IllegalArgumentException.class,
                () -> exporter.exportResults(null, metrics, latencyStats, output));
    }

    @Test
    @DisplayName("exportResults: throws when metrics is null")
    void throwsWhenMetricsNull() {
        File output = tempDir.resolve("err.json").toFile();
        assertThrows(IllegalArgumentException.class,
                () -> exporter.exportResults("test", null, latencyStats, output));
    }

    @Test
    @DisplayName("exportResults: throws when latencyStats is null")
    void throwsWhenLatencyStatsNull() {
        File output = tempDir.resolve("err.json").toFile();
        assertThrows(IllegalArgumentException.class,
                () -> exporter.exportResults("test", metrics, null, output));
    }

    @Test
    @DisplayName("exportResults: throws when outputFile is null")
    void throwsWhenOutputFileNull() {
        assertThrows(IllegalArgumentException.class,
                () -> exporter.exportResults("test", metrics, latencyStats, null));
    }

    // -------- helper --------

    private TestMetrics buildMetrics() {
        TestMetrics m = new TestMetrics();
        m.setStartTime(1_000_000L);
        m.setEndTime(1_060_000L);
        m.setDuration(60_000L);
        m.setAverageTps(5.0);
        m.incrementTotalRequests();
        m.incrementSuccessCount();
        m.updateHistogramSnapshots();
        return m;
    }
}
