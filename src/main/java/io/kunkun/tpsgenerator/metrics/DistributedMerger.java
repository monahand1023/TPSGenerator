package io.kunkun.tpsgenerator.metrics;

import org.HdrHistogram.Histogram;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges the JSON result documents of several independent runs (e.g. one per node in a distributed
 * test) into a single combined document. Counts are summed and offered TPS is added across nodes;
 * latency percentiles are recomputed from the merged HdrHistogram (averaging percentiles would be
 * statistically wrong). This is the aggregation half of distributed load generation: run N nodes at
 * {@code targetTps/N} each, then merge their result files.
 */
public final class DistributedMerger {

    private DistributedMerger() {
    }

    public static Map<String, Object> merge(List<Map<String, Object>> runs) {
        if (runs == null || runs.isEmpty()) {
            throw new IllegalArgumentException("at least one run document is required");
        }

        Histogram combined = new Histogram(3);
        long total = 0;
        long success = 0;
        long failure = 0;
        long timeout = 0;
        double offeredTps = 0.0;
        long maxDuration = 0;

        for (Map<String, Object> run : runs) {
            total += num(run.get("totalRequests"));
            success += num(run.get("successCount"));
            failure += num(run.get("failureCount"));
            timeout += num(run.get("timeoutCount"));
            offeredTps += dbl(run.get("averageTps"));
            maxDuration = Math.max(maxDuration, (long) (dbl(run.get("durationSeconds")) * 1000));

            Map<String, Object> latency = asMap(run.get("latency"));
            Object encoded = latency.get("histogram");
            if (encoded instanceof String) {
                combined.add(HistogramCodec.decode((String) encoded));
            }
        }

        boolean haveLatency = combined.getTotalCount() > 0;

        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("p50Ms", haveLatency ? combined.getValueAtPercentile(50) : 0);
        latency.put("p95Ms", haveLatency ? combined.getValueAtPercentile(95) : 0);
        latency.put("p99Ms", haveLatency ? combined.getValueAtPercentile(99) : 0);
        latency.put("maxMs", haveLatency ? combined.getMaxValue() : 0);
        latency.put("meanMs", haveLatency ? round3(combined.getMean()) : 0.0);
        latency.put("histogram", HistogramCodec.encode(combined));

        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("testName", "merged (" + runs.size() + " runs)");
        merged.put("nodeCount", runs.size());
        merged.put("durationSeconds", round3(maxDuration / 1000.0));
        merged.put("totalRequests", total);
        merged.put("successCount", success);
        merged.put("failureCount", failure);
        merged.put("timeoutCount", timeout);
        merged.put("successRate", total > 0 ? round4((double) success / total) : 0.0);
        merged.put("averageTps", round3(offeredTps));
        merged.put("latency", latency);
        return merged;
    }

    private static long num(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private static double dbl(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
