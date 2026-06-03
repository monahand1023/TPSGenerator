package io.kunkun.tpsgenerator.metrics;

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DistributedMergerTest {

    private Map<String, Object> runDoc(long total, long success, double avgTps, Histogram h) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("totalRequests", total);
        doc.put("successCount", success);
        doc.put("failureCount", total - success);
        doc.put("averageTps", avgTps);
        doc.put("durationSeconds", 10.0);
        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("histogram", HistogramCodec.encode(h));
        doc.put("latency", latency);
        return doc;
    }

    @Test
    void mergesCountsTpsAndHistogram() {
        Histogram h1 = new Histogram(3);
        for (int i = 0; i < 100; i++) h1.recordValue(10);
        Histogram h2 = new Histogram(3);
        for (int i = 0; i < 100; i++) h2.recordValue(20);

        Map<String, Object> merged = DistributedMerger.merge(List.of(
                runDoc(100, 99, 50.0, h1),
                runDoc(100, 98, 50.0, h2)));

        assertEquals(200L, ((Number) merged.get("totalRequests")).longValue());
        assertEquals(2, ((Number) merged.get("nodeCount")).intValue());
        assertEquals(100.0, ((Number) merged.get("averageTps")).doubleValue(), 0.01); // 50 + 50
        assertEquals(0.985, ((Number) merged.get("successRate")).doubleValue(), 0.001); // 197/200

        @SuppressWarnings("unchecked")
        Map<String, Object> latency = (Map<String, Object>) merged.get("latency");
        long p50 = ((Number) latency.get("p50Ms")).longValue();
        long max = ((Number) latency.get("maxMs")).longValue();
        assertTrue(p50 >= 9 && p50 <= 11, "merged p50 ~10ms, was " + p50);
        assertTrue(max >= 19 && max <= 21, "merged max ~20ms, was " + max);
        // The merged histogram is itself encoded so merges can chain.
        assertEquals(200, HistogramCodec.decode((String) latency.get("histogram")).getTotalCount());
    }

    @Test
    void emptyInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> DistributedMerger.merge(List.of()));
    }
}
