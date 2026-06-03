package io.kunkun.tpsgenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the CLI exit-code contract via the testable {@code run(args):int} entry point.
 * Codes: 0 ok, 1 usage/error, 2 error-rate threshold, 3 SLA breach, 4 compare regression.
 */
class TPSGeneratorApplicationRunTest {

    @TempDir
    Path tempDir;

    // ---- usage / error ----

    @Test
    @DisplayName("no args -> exit 1")
    void noArgsReturnsOne() {
        assertEquals(1, TPSGeneratorApplication.run(new String[]{}));
    }

    @Test
    @DisplayName("missing config file -> exit 1")
    void missingConfigReturnsOne() {
        assertEquals(1, TPSGeneratorApplication.run(
                new String[]{tempDir.resolve("does-not-exist.json").toString(),
                        tempDir.resolve("out").toString()}));
    }

    // ---- compare (exit 4 on regression) ----

    @Test
    @DisplayName("compare: regression -> exit 4, otherwise 0")
    void compareExitCodes() throws Exception {
        Path baseline = writeJson("baseline.json", resultDoc(0.99, 50, 20));
        Path good = writeJson("good.json", resultDoc(0.99, 50, 20));
        Path bad = writeJson("bad.json", resultDoc(0.99, 50, 80)); // p95 20 -> 80

        assertEquals(0, TPSGeneratorApplication.run(
                new String[]{"compare", baseline.toString(), good.toString()}));
        assertEquals(4, TPSGeneratorApplication.run(
                new String[]{"compare", baseline.toString(), bad.toString()}));
        assertEquals(1, TPSGeneratorApplication.run(new String[]{"compare", baseline.toString()})); // too few args
    }

    // ---- merge (exit 0, writes output) ----

    @Test
    @DisplayName("merge: combines runs into an output file -> exit 0")
    void mergeWritesOutput() throws Exception {
        Path a = writeJson("a.json", resultDoc(0.99, 50, 20));
        Path b = writeJson("b.json", resultDoc(0.98, 50, 25));
        Path out = tempDir.resolve("merged.json");

        assertEquals(0, TPSGeneratorApplication.run(
                new String[]{"merge", out.toString(), a.toString(), b.toString()}));
        assertTrue(Files.exists(out), "merge should write the output file");
        assertTrue(Files.readString(out).contains("totalRequests"));
    }

    // ---- run exit codes 2 (error-rate) and 3 (SLA) against an unreachable target ----

    @Test
    @DisplayName("error rate over fail-threshold -> exit 2")
    void errorRateThresholdReturnsTwo() throws Exception {
        // Unreachable target => every request fails; failThresholdErrorRate=0 => exit 2.
        Path cfg = writeJson("cfg2.json", runConfig(0.0, null));
        int code = TPSGeneratorApplication.run(new String[]{cfg.toString(), tempDir.resolve("o2").toString()});
        assertEquals(2, code);
    }

    @Test
    @DisplayName("SLA breach (no error-rate trip) -> exit 3")
    void slaBreachReturnsThree() throws Exception {
        // failThresholdErrorRate=1.0 so the error-rate check never trips; minSuccessRate=0.5 with
        // an all-failing run (successRate 0) => SLA breach => exit 3.
        Path cfg = writeJson("cfg3.json", runConfig(1.0, "\"sla\": {\"minSuccessRate\": 0.5},"));
        int code = TPSGeneratorApplication.run(new String[]{cfg.toString(), tempDir.resolve("o3").toString()});
        assertEquals(3, code);
    }

    // ---- helpers ----

    private Path writeJson(String name, String content) throws Exception {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    /** A minimal JSON result document the comparator/merger understand. */
    private String resultDoc(double successRate, double avgTps, double p95) {
        return String.format(java.util.Locale.US,
                "{\"totalRequests\":1000,\"successCount\":990,\"failureCount\":10,"
                        + "\"successRate\":%s,\"averageTps\":%s,"
                        + "\"latency\":{\"p50Ms\":10,\"p95Ms\":%s,\"p99Ms\":%s,\"maxMs\":%s,\"meanMs\":12}}",
                successRate, avgTps, p95, p95 * 1.5, p95 * 2);
    }

    /** A load-test config aimed at an unreachable target (fast failures) with a short duration. */
    private String runConfig(double failThreshold, String slaBlockOrNull) {
        String sla = slaBlockOrNull == null ? "" : slaBlockOrNull;
        return "{"
                + "\"name\":\"exit-test\","
                + "\"targetServiceUrl\":\"http://localhost:1\","
                + "\"testDuration\":\"300ms\","
                + "\"trafficPattern\":{\"type\":\"stable\",\"targetTps\":20},"
                + "\"requestTemplates\":[{\"name\":\"r\",\"method\":\"GET\",\"urlTemplate\":\"http://localhost:1/x\"}],"
                + "\"metrics\":{\"resourceMonitoring\":{\"enabled\":false,\"sampleInterval\":\"5s\"}},"
                + sla
                + "\"failThresholdErrorRate\":" + failThreshold
                + "}";
    }
}
