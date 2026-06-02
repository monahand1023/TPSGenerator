package io.kunkun.tpsgenerator.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two TPS Generator JSON result documents (as produced by
 * {@link io.kunkun.tpsgenerator.metrics.exporter.JsonExporter}) and reports the deltas, flagging
 * regressions that exceed configured thresholds. Pure logic so it is unit-testable; the CLI wrapper
 * in {@code TPSGeneratorApplication} handles file IO and exit codes.
 */
public final class RunComparator {

    private RunComparator() {
    }

    /** The latency metrics (under the {@code "latency"} object) compared as percentage changes. */
    private static final String[] LATENCY_KEYS = {"p50Ms", "p95Ms", "p99Ms", "maxMs", "meanMs"};

    /**
     * Result of a comparison: human-readable diff lines and any threshold breaches.
     */
    public static final class Result {
        private final List<String> lines;
        private final List<String> regressions;

        Result(List<String> lines, List<String> regressions) {
            this.lines = lines;
            this.regressions = regressions;
        }

        public List<String> getLines() {
            return lines;
        }

        public List<String> getRegressions() {
            return regressions;
        }

        public boolean hasRegressions() {
            return !regressions.isEmpty();
        }
    }

    /**
     * Compares a baseline document against a candidate document.
     *
     * @param baseline               baseline result document
     * @param candidate              candidate result document
     * @param maxLatencyRegressionPct latency percentile increase (percent) that counts as a regression
     * @param maxSuccessRateDrop     absolute success-rate drop (0..1) that counts as a regression
     * @return the comparison result
     */
    public static Result compare(Map<String, Object> baseline, Map<String, Object> candidate,
                                 double maxLatencyRegressionPct, double maxSuccessRateDrop) {
        List<String> lines = new ArrayList<>();
        List<String> regressions = new ArrayList<>();

        // Success rate (absolute drop)
        double baseSr = num(baseline.get("successRate"));
        double candSr = num(candidate.get("successRate"));
        lines.add(String.format("successRate: %.4f -> %.4f (%+.4f)", baseSr, candSr, candSr - baseSr));
        if (baseSr - candSr > maxSuccessRateDrop) {
            regressions.add(String.format("success rate dropped %.4f (> %.4f)", baseSr - candSr, maxSuccessRateDrop));
        }

        // Average TPS (informational + percent)
        double baseTps = num(baseline.get("averageTps"));
        double candTps = num(candidate.get("averageTps"));
        lines.add(String.format("averageTps: %.2f -> %.2f (%s)", baseTps, candTps, pct(baseTps, candTps)));

        // Latency percentiles (percent increase = regression)
        Map<String, Object> baseLat = asMap(baseline.get("latency"));
        Map<String, Object> candLat = asMap(candidate.get("latency"));
        for (String key : LATENCY_KEYS) {
            double b = num(baseLat.get(key));
            double c = num(candLat.get(key));
            double changePct = b > 0 ? (c - b) / b * 100.0 : (c > 0 ? Double.POSITIVE_INFINITY : 0.0);
            lines.add(String.format("latency.%s: %.3f -> %.3f ms (%s)", key, b, c, pct(b, c)));
            if (changePct > maxLatencyRegressionPct) {
                regressions.add(String.format("%s regressed %.1f%% (> %.1f%%): %.3f -> %.3f ms",
                        key, changePct, maxLatencyRegressionPct, b, c));
            }
        }

        return new Result(lines, regressions);
    }

    private static String pct(double base, double candidate) {
        if (base == 0.0) {
            return candidate == 0.0 ? "+0.0%" : "n/a";
        }
        return String.format("%+.1f%%", (candidate - base) / base * 100.0);
    }

    private static double num(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }
}
