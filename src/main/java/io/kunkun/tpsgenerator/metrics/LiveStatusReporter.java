package io.kunkun.tpsgenerator.metrics;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders a live, in-place status line to the console during a run (enabled with {@code --live}).
 * Refreshes once per second using a carriage return so the single line updates in place. Reads
 * everything from the {@link MetricsCollector} on its own daemon thread; never throws into the run.
 */
public class LiveStatusReporter {

    private final MetricsCollector metrics;
    private final long totalDurationMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "live-status");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LiveStatusReporter(MetricsCollector metrics, long totalDurationMs) {
        this.metrics = metrics;
        this.totalDurationMs = totalDurationMs;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(this::render, 1, 1, TimeUnit.SECONDS);
        }
    }

    /** Stops the reporter, draws one final line, and moves to a fresh line. */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            render();
            System.out.println();
        }
    }

    private void render() {
        try {
            long start = metrics.getStartTime();
            long elapsed = start > 0 ? System.currentTimeMillis() - start : 0;
            double pct = totalDurationMs > 0 ? Math.min(100.0, 100.0 * elapsed / totalDurationMs) : 0.0;
            TestMetrics tm = metrics.getTestMetrics();
            String line = formatStatusLine(pct, elapsed, metrics.getCurrentTps(),
                    tm.getSuccessRate() * 100.0,
                    tm.getResponseTimePercentile(50),
                    tm.getResponseTimePercentile(95),
                    tm.getResponseTimePercentile(99),
                    tm.getTotalRequests());
            System.out.print("\r" + line);
            System.out.flush();
        } catch (Exception ignored) {
            // Live status is best-effort; never disturb the run.
        }
    }

    /**
     * Formats the status line (pure, for testing). Trailing spaces pad over any longer prior line.
     */
    public static String formatStatusLine(double pct, long elapsedMs, double tps, double successPct,
                                          long p50, long p95, long p99, long totalRequests) {
        return String.format(
                "[%5.1f%%] %s | TPS %7.1f | OK %5.1f%% | p50/p95/p99 %d/%d/%d ms | reqs %d      ",
                pct, formatElapsed(elapsedMs), tps, successPct, p50, p95, p99, totalRequests);
    }

    private static String formatElapsed(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
