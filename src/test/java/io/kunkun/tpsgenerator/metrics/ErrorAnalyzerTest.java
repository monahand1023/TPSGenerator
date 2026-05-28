package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorAnalyzer.
 */
class ErrorAnalyzerTest {

    private ErrorAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ErrorAnalyzer();
    }

    // -------- recordException --------

    @Test
    @DisplayName("recordException: increments count for exception type")
    void recordExceptionIncrementsCount() {
        analyzer.recordException(new RuntimeException("boom"));
        Map<String, Long> counts = analyzer.getExceptionCounts();
        assertEquals(1L, counts.getOrDefault(RuntimeException.class.getName(), 0L));
    }

    @Test
    @DisplayName("recordException: tracks different exception types separately")
    void recordExceptionTracksDifferentTypes() {
        analyzer.recordException(new RuntimeException("r"));
        analyzer.recordException(new IllegalArgumentException("i"));
        Map<String, Long> counts = analyzer.getExceptionCounts();
        assertEquals(1L, counts.get(RuntimeException.class.getName()));
        assertEquals(1L, counts.get(IllegalArgumentException.class.getName()));
    }

    @Test
    @DisplayName("recordException: accumulates multiple calls of same type")
    void recordExceptionAccumulates() {
        for (int i = 0; i < 5; i++) {
            analyzer.recordException(new RuntimeException("e" + i));
        }
        assertEquals(5L, analyzer.getTotalExceptionCount());
    }

    @Test
    @DisplayName("recordException: stores exception samples")
    void recordExceptionStoresSamples() {
        analyzer.recordException(new RuntimeException("sample message"));
        Map<String, List<ErrorAnalyzer.ExceptionSample>> samples = analyzer.getExceptionSamples();
        assertFalse(samples.isEmpty());
        List<ErrorAnalyzer.ExceptionSample> list = samples.get(RuntimeException.class.getName());
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertEquals("sample message", list.get(0).getMessage());
    }

    // -------- recordErrorResponse --------

    @Test
    @DisplayName("recordErrorResponse: records 4xx responses")
    void recordErrorResponseFor4xx() {
        analyzer.recordErrorResponse(404, "Not Found");
        assertEquals(1, analyzer.getTotalErrorResponseCount());
    }

    @Test
    @DisplayName("recordErrorResponse: records 5xx responses")
    void recordErrorResponseFor5xx() {
        analyzer.recordErrorResponse(500, "Internal Server Error");
        assertEquals(1, analyzer.getTotalErrorResponseCount());
    }

    @Test
    @DisplayName("recordErrorResponse: ignores 2xx responses")
    void recordErrorResponseIgnores2xx() {
        analyzer.recordErrorResponse(200, "OK");
        assertEquals(0, analyzer.getTotalErrorResponseCount());
    }

    @Test
    @DisplayName("recordErrorResponse: handles null body gracefully")
    void recordErrorResponseHandlesNullBody() {
        assertDoesNotThrow(() -> analyzer.recordErrorResponse(500, null));
        assertEquals(1, analyzer.getTotalErrorResponseCount());
    }

    // -------- getTopErrorStatusCodes --------

    @Test
    @DisplayName("getTopErrorStatusCodes: returns codes in descending frequency order")
    void topErrorStatusCodesDescendingOrder() {
        // 500 x3, 404 x2, 503 x1
        for (int i = 0; i < 3; i++) analyzer.recordErrorResponse(500, "error");
        for (int i = 0; i < 2; i++) analyzer.recordErrorResponse(404, "not found");
        analyzer.recordErrorResponse(503, "unavailable");

        Map<Integer, Integer> top = analyzer.getTopErrorStatusCodes(3);
        List<Integer> codes = List.copyOf(top.keySet());

        assertEquals(500, codes.get(0));
        assertEquals(404, codes.get(1));
        assertEquals(503, codes.get(2));
    }

    @Test
    @DisplayName("getTopErrorStatusCodes: limit is respected")
    void topErrorStatusCodesLimitRespected() {
        analyzer.recordErrorResponse(500, "e");
        analyzer.recordErrorResponse(404, "e");
        analyzer.recordErrorResponse(503, "e");

        Map<Integer, Integer> top = analyzer.getTopErrorStatusCodes(2);
        assertEquals(2, top.size());
    }

    // -------- generateErrorReport --------

    @Test
    @DisplayName("generateErrorReport: returns non-null report")
    void generateErrorReportNotNull() {
        analyzer.recordException(new RuntimeException("boom"));
        analyzer.recordErrorResponse(500, "error");
        ErrorAnalyzer.ErrorReport report = analyzer.generateErrorReport();
        assertNotNull(report);
        assertEquals(1L, report.getTotalExceptionCount());
        assertEquals(1, report.getTotalErrorResponseCount());
        assertNotNull(report.getTopExceptions());
        assertNotNull(report.getTopErrorStatusCodes());
    }

    @Test
    @DisplayName("generateErrorReport: empty analyzer produces zeroed report")
    void generateErrorReportEmpty() {
        ErrorAnalyzer.ErrorReport report = analyzer.generateErrorReport();
        assertEquals(0L, report.getTotalExceptionCount());
        assertEquals(0, report.getTotalErrorResponseCount());
    }

    // -------- thread safety --------

    @Test
    @DisplayName("Concurrent recordException calls do not throw")
    void concurrentRecordExceptionThreadSafe() throws InterruptedException {
        int threads = 10;
        int perThread = 100;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        analyzer.recordException(new RuntimeException("concurrent " + i));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals((long) threads * perThread, analyzer.getTotalExceptionCount());
    }

    @Test
    @DisplayName("Concurrent recordErrorResponse calls do not throw")
    void concurrentRecordErrorResponseThreadSafe() throws InterruptedException {
        int threads = 10;
        int perThread = 50;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        analyzer.recordErrorResponse(500, "body");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // getTotalErrorResponseCount counts samples stored (bounded by MAX_ERROR_SAMPLES=100)
        assertTrue(analyzer.getTotalErrorResponseCount() > 0);
    }
}
