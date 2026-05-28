package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.metrics.exporter.CSVExporter;
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
 * Unit tests for CSVExporter.
 */
class CSVExporterTest {

    @TempDir
    Path tempDir;

    private CSVExporter exporter;
    private TestMetrics metrics;

    @BeforeEach
    void setUp() {
        exporter = new CSVExporter();
        metrics = buildMetrics();
    }

    @Test
    @DisplayName("exportMetrics: produces non-empty file")
    void exportMetricsProducesNonEmptyFile() throws IOException {
        File output = tempDir.resolve("metrics.csv").toFile();
        exporter.exportMetrics(metrics, output);
        assertTrue(output.exists(), "Output file should exist");
        assertTrue(output.length() > 0, "Output file should be non-empty");
    }

    @Test
    @DisplayName("exportMetrics: CSV output contains 'Metric' header")
    void exportMetricsContainsMetricHeader() throws IOException {
        File output = tempDir.resolve("headers.csv").toFile();
        exporter.exportMetrics(metrics, output);
        String content = Files.readString(output.toPath());
        assertTrue(content.contains("Metric"), "CSV should contain 'Metric' header column");
        assertTrue(content.contains("Value"), "CSV should contain 'Value' header column");
    }

    @Test
    @DisplayName("exportMetrics: CSV contains expected row labels")
    void exportMetricsContainsRowLabels() throws IOException {
        File output = tempDir.resolve("rows.csv").toFile();
        exporter.exportMetrics(metrics, output);
        String content = Files.readString(output.toPath());
        assertTrue(content.contains("Total Requests"), "Expected 'Total Requests' row");
        assertTrue(content.contains("Success Rate"), "Expected 'Success Rate' row");
        assertTrue(content.contains("Average TPS"), "Expected 'Average TPS' row");
    }

    @Test
    @DisplayName("exportMetrics: with LatencyStats includes HDR rows")
    void exportMetricsWithLatencyStatsIncludesHdrRows() throws IOException {
        LatencyStats latencyStats = new LatencyStats(1.0, 2.0, 3.0, 5.0, 1.5);
        File output = tempDir.resolve("latency.csv").toFile();
        exporter.exportMetrics(metrics, latencyStats, output);
        String content = Files.readString(output.toPath());
        assertTrue(content.contains("HDR P50"), "Expected HDR P50 row");
        assertTrue(content.contains("HDR P95"), "Expected HDR P95 row");
        assertTrue(content.contains("HDR P99"), "Expected HDR P99 row");
    }

    @Test
    @DisplayName("exportMetrics: without LatencyStats does not include HDR rows")
    void exportMetricsWithoutLatencyStatsOmitsHdrRows() throws IOException {
        File output = tempDir.resolve("no_latency.csv").toFile();
        exporter.exportMetrics(metrics, null, output);
        String content = Files.readString(output.toPath());
        assertFalse(content.contains("HDR P50"), "HDR rows should be absent when latency is null");
    }

    @Test
    @DisplayName("exportMetrics: TPS samples file is also created")
    void exportMetricsCreatesTpsSamplesFile() throws IOException {
        File output = tempDir.resolve("metrics.csv").toFile();
        exporter.exportMetrics(metrics, output);
        File tpsSamples = new File(tempDir.toFile(), "tps_samples.csv");
        assertTrue(tpsSamples.exists(), "TPS samples file should be created");
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
        m.recordStatusCode(200);
        m.updateHistogramSnapshots();
        return m;
    }
}
